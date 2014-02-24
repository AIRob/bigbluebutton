 package org.bigbluebutton.core.apps.users

import org.bigbluebutton.core.api._
import scala.collection.mutable.HashMap
import org.bigbluebutton.core.User
import java.util.ArrayList
import net.lag.logging.Logger
import org.bigbluebutton.core.MeetingActor

trait UsersApp {
  this : MeetingActor =>
  
  private val log = Logger.get
  val outGW: MessageOutGateway
  
  private val users = new UsersModel
  private var regUsers = new collection.immutable.HashMap[String, RegisteredUser]
  
  private var locked = false
  private var meetingMuted = false
  private var currentPresenter = new Presenter("system", "system", "system")
  
  def hasUser(userID: String):Boolean = {
    users.hasUser(userID)
  }
  
  def getUser(userID:String):Option[UserVO] = {
    users.getUser(userID)
  }
  
  def getCurrentPresenter:Presenter = {
    currentPresenter
  }
  
  def handleMuteMeetingRequest(msg: MuteMeetingRequest) {
    meetingMuted = msg.mute
    
//    users2.unlockedUsers map ({ u =>
//      outGW.send(new MuteVoiceUser(meetingID, recorded, msg.requesterID, u.voice.id, msg.mute))
//    })
  }
  
  def handleValidateAuthToken(msg: ValidateAuthToken) {
    regUsers.get (msg.userId) match {
      case Some(u) => outGW.send(new ValidateAuthTokenReply(meetingID, msg.userId, msg.token, true))
      case None => outGW.send(new ValidateAuthTokenReply(meetingID, msg.userId, msg.token, false))
    }  
  }
  
  def handleRegisterUser(msg: RegisterUser) {
    val regUser = new RegisteredUser(msg.userID, msg.extUserID, msg.name, msg.role)
    regUsers += msg.userID -> regUser
    outGW.send(new UserRegistered(meetingID, recorded, regUser))
  }
  
  def handleIsMeetingMutedRequest(msg: IsMeetingMutedRequest) {
    outGW.send(new IsMeetingMutedReply(meetingID, recorded, msg.requesterID, meetingMuted))
  }
  
  def handleMuteUserRequest(msg: MuteUserRequest) {
    println("Received mute user request uid=[" + msg.userID + "] mute=[" + msg.mute + "]")
    users.getUser(msg.userID) match {
      case Some(u) => {
        println("Sending mute user request uid=[" + msg.userID + "] mute=[" + msg.mute + "]")
        outGW.send(new MuteVoiceUser(meetingID, recorded, msg.requesterID, u.userID, msg.mute))
      }
      case None => {
        println("Could not find user to mute. uid=[" + msg.userID + "] mute=[" + msg.mute + "]")
      }
    }
  }
  
  def handleLockUserRequest(msg: LockUserRequest) {
//    users2.lockVoice(msg.userID, msg.lock)
  }
  
  def handleEjectUserRequest(msg: EjectUserRequest) {
    println("Received eject user request uid=[" + msg.userID + "]")
    users.getUser(msg.userID) match {
      case Some(u) => outGW.send(new EjectVoiceUser(meetingID, recorded, msg.requesterID, u.userID))
      case None => // do nothing
    }
  }
   
  def handleLockUser(msg: LockUser) {
    
  }
  
  def handleLockAllUsers(msg: LockAllUsers) {
    
  }
  
  def handleGetLockSettings(msg: GetLockSettings) {
    
  }
  
  def handleIsMeetingLocked(msg: IsMeetingLocked) {
    
  }
	      
  def handleSetLockSettings(msg: SetLockSettings) {
    if (permissions != msg.settings) {
      permissions = msg.settings
      outGW.send(new NewPermissionsSetting(meetingID, permissions))
    }    
  }
  
  def handleInitLockSettings(msg: InitLockSettings) {
    if (permissions != msg.settings || locked != msg.locked) {
	    permissions = msg.settings   
	    locked = msg.locked	    
	    outGW.send(new PermissionsSettingInitialized(msg.meetingID, msg.locked, msg.settings))
    }
  }  

  def handleUserRaiseHand(msg: UserRaiseHand) {
    users.getUser(msg.userId) foreach {user =>
      val uvo = user.copy(raiseHand=true)
      users.addUser(uvo)
      outGW.send(new UserRaisedHand(meetingID, recorded, uvo.userID))
    }
  }

  def handleUserLowerHand(msg: UserLowerHand) {
    users.getUser(msg.userId) foreach {user =>
      val uvo = user.copy(raiseHand=false)
      users.addUser(uvo)
      outGW.send(new UserLoweredHand(meetingID, recorded, uvo.userID, msg.loweredBy))
    }    
  }

  def handleUserShareWebcam(msg: UserShareWebcam) {
    users.getUser(msg.userId) foreach {user =>
      val uvo = user.copy(hasStream=true, webcamStream=msg.stream)
      users.addUser(uvo)
      outGW.send(new UserSharedWebcam(meetingID, recorded, uvo.userID, msg.stream))
    }     
  }

  def handleUserunshareWebcam(msg: UserUnshareWebcam) {
    users.getUser(msg.userId) foreach {user =>
      val stream = user.webcamStream
      val uvo = user.copy(hasStream=false, webcamStream="")
      users.addUser(uvo)
      outGW.send(new UserUnsharedWebcam(meetingID, recorded, uvo.userID, stream))
    }     
  }
	                         
  def handleChangeUserStatus(msg: ChangeUserStatus):Unit = {    
	if (users.hasUser(msg.userID)) {
		  outGW.send(new UserStatusChange(meetingID, recorded, msg.userID, msg.status, msg.value))
	}  
  }
  
  def handleGetUsers(msg: GetUsers):Unit = {
	  outGW.send(new GetUsersReply(msg.meetingID, msg.requesterID, users.getUsers))
  }
  
  def handleUserJoin(msg: UserJoining):Unit = {
  	log.debug("UsersApp: init handleUserJoin")
    val vu = new VoiceUser(msg.userID, msg.userID, msg.name, msg.name,  
                           false, false, false, false)
    val uvo = new UserVO(msg.userID, msg.extUserID, msg.name, 
                  msg.role, raiseHand=false, presenter=false, 
                  hasStream=false, locked=false, webcamStream="", 
                  phoneUser=false, vu, permissions.permissions)
  	
	users.addUser(uvo)
					
	outGW.send(new UserJoined(meetingID, recorded, uvo))
	
	// Become presenter if the only moderator		
	if (users.numModerators == 1) {
	  if (msg.role == Role.MODERATOR) {
		assignNewPresenter(msg.userID, msg.name, msg.userID)
	  }	  
	}
  }
			
  def handleUserLeft(msg: UserLeaving):Unit = {
	 if (users.hasUser(msg.userID)) {
	  val user = users.removeUser(msg.userID)
	  user foreach (u => outGW.send(new UserLeft(msg.meetingID, recorded, u)))
	  
	 }
   else{
    log.warning("This user is not here:" + msg.userID)
   }
  }

  def handleVoiceUserJoined(msg: VoiceUserJoined) = {
      val user = users.getUser(msg.voiceUser.webUserId) match {
        case Some(user) => {
          val nu = user.copy(voiceUser=msg.voiceUser)
          users.addUser(nu)
          println("Received user joined voice for user [" + nu.name + "] userid=[" + msg.voiceUser.webUserId + "]" )
          outGW.send(new UserJoinedVoice(meetingID, recorded, voiceBridge, nu))
        }
        case None => {
          // No current web user. This means that the user called in through
          // the phone. We need to generate a new user as we are not able
          // to match with a web user.
          val webUserId = users.generateWebUserId
          val vu = new VoiceUser(msg.voiceUser.userId, webUserId, 
                                 msg.voiceUser.callerName, msg.voiceUser.callerNum,
                                 true, false, false, false)
          val uvo = new UserVO(webUserId, webUserId, msg.voiceUser.callerName, 
		                  Role.VIEWER, raiseHand=false, presenter=false, 
		                  hasStream=false, locked=false, webcamStream="", 
		                  phoneUser=true, vu, permissions.permissions)
		  	
		  users.addUser(uvo)
		  println("New user joined voice for user [" + uvo.name + "] userid=[" + msg.voiceUser.webUserId + "]")
		  outGW.send(new UserJoined(meetingID, recorded, uvo))
        }
      }
  }
  
  def handleVoiceUserLeft(msg: VoiceUserLeft) {
    users.getUser(msg.userId) foreach {user =>
      val vu = new VoiceUser(user.userID, user.userID, user.name, user.name,  
                           false, false, false, false)
      val nu = user.copy(voiceUser=vu)
      users.addUser(nu)
            
      println("Received voice user left =[" + user.name + "] wid=[" + msg.userId + "]" )
      outGW.send(new UserLeftVoice(meetingID, recorded, voiceBridge, nu))        
    }    
  }
  
  def handleVoiceUserMuted(msg: VoiceUserMuted) {
    users.getUser(msg.userId) foreach {user =>
      val nv = user.voiceUser.copy(muted=msg.muted)
      val nu = user.copy(voiceUser=nv)
      users.addUser(nu)
      println("Received voice muted=[" + msg.muted + "] wid=[" + msg.userId + "]" )
      outGW.send(new UserVoiceMuted(meetingID, recorded, voiceBridge, nu))        
    }   
  }
  
  def handleVoiceUserTalking(msg: VoiceUserTalking) {
    users.getUser(msg.userId) foreach {user =>
      val nv = user.voiceUser.copy(talking=msg.talking)
      val nu = user.copy(voiceUser=nv)
      users.addUser(nu)
      println("Received voice talking=[" + msg.talking + "] wid=[" + msg.userId + "]" )
      outGW.send(new UserVoiceTalking(meetingID, recorded, voiceBridge, nu))        
    }     
  }
  
  def handleAssignPresenter(msg: AssignPresenter):Unit = {
	assignNewPresenter(msg.newPresenterID, msg.newPresenterName, msg.assignedBy)
  } 
	
  def assignNewPresenter(newPresenterID:String, newPresenterName: String, assignedBy: String) {
    if (users.hasUser(newPresenterID)) {

      users.getCurrentPresenter match {
        case Some(curPres) => {
  	      users.unbecomePresenter(curPres.userID)  
  	      outGW.send(new UserStatusChange(meetingID, recorded, curPres.userID, "presenter", false:java.lang.Boolean))        
        }
        case None => // do nothing
      }
      
  	  users.getUser(newPresenterID) match {
  	    case Some(newPres) => {
  	      users.becomePresenter(newPres.userID)      	  
  	      currentPresenter = new Presenter(newPresenterID, newPresenterName, assignedBy)
  	      outGW.send(new PresenterAssigned(meetingID, recorded, new Presenter(newPresenterID, newPresenterName, assignedBy)))
          outGW.send(new UserStatusChange(meetingID, recorded, newPresenterID, "presenter", true:java.lang.Boolean))  	      
  	    }
  	    case None => // do nothing
  	  }

    }
  }
}