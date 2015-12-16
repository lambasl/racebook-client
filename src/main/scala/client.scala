import scala.util.{ Success, Failure }
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.event.Logging
import akka.io.IO
import spray.json.{ JsonFormat, DefaultJsonProtocol }
import spray.can.Http
import spray.httpx.SprayJsonSupport
import spray.client.pipelining._
import spray.util._
import scala.concurrent.Future
import spray.http.{ HttpRequest, HttpResponse }
import spray.json._
import spray.routing._
import spray.httpx.SprayJsonSupport
import spray.json.AdditionalFormats
import akka.actor._
import scala.concurrent.{ Future, Promise }
import scala.concurrent._
import duration._
import scala.util.Random
import java.security.KeyPair
import java.security.KeyPairGenerator
import sun.misc.BASE64Encoder
import sun.misc.BASE64Decoder
import java.security.interfaces.RSAPublicKey
import java.security.spec.EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.security.KeyFactory
import javax.crypto.spec.SecretKeySpec
import javax.crypto.Cipher
import java.nio.file.{Files, Paths}

case class AddUser(name: String, pubKey: String)
case class YourUserID(id: String)
case class LikeMyPost(postID: String)
case class CommentOnMyPost(postID: String)
case class AddMeAsFriend(id: String)
case class CreateAlbum(owner: String, name: String, permission:String)
case class AddPhoto(user: String, photo: String, album: String, permission: String)

case class CreatePost(user: String, data: String, encryptedKey: String, permission: String)
case class Like(id: String, typ: String, userName: String, userId: String)
case class Comment(id: String, typ: String, userName: String, userId: String, comment: String, encryptedKey: String)
case class AddFriend(from: String, to: String)
object FacebookJSONProtocol extends DefaultJsonProtocol {
  implicit val format = jsonFormat2(AddUser.apply)
  implicit val format2 = jsonFormat4(CreatePost.apply)
  implicit val format3 = jsonFormat4(Like.apply)
  implicit val format4 = jsonFormat6(Comment.apply)
  implicit val format5 = jsonFormat2(AddFriend.apply)
  implicit val format6 = jsonFormat3(CreateAlbum.apply)
  implicit val format7 = jsonFormat4(AddPhoto.apply)
}

object Client extends App {
  var userToKeysMap = Map[String, KeyPair]()
  val encoder = new BASE64Encoder
  val decoder = new BASE64Decoder
  
  

  var totalNumberOfActors: Int = 10//1000
  if (args.length != 1) {
    println("Number of users not specified. Using default value of 10000")
  } else {
    totalNumberOfActors = args(0).toInt
  }
  val aggActors: Int = 0//(0.2 * totalNumberOfActors).asInstanceOf[Int]
  val modActors: Int = 0//(0.5 * totalNumberOfActors).asInstanceOf[Int]
  val dormantActors: Int = 1//(0.3 * totalNumberOfActors).asInstanceOf[Int]
  var totalRequests: Int = 0
  var createPostReqs: Int = 0
  var fetchPostReqs: Int = 0
  var likeRequests: Int = 0
  var createCommentReqs: Int = 0
  var fetchProfileReqs: Int = 0
  var fetchFriendsReqs: Int = 0
  var uploadPhotoReqs: Int = 0
  var fetchPhotos: Int = 0
  var createAlbumReqs: Int = 0
  var fetchAlbumReqs: Int = 0
  implicit val system = ActorSystem("client-system")
  import system.dispatcher
  val log = Logging(system, getClass)
  log.info("Starting Tracebook.com...")
  val master = system.actorOf(Props(new Master()), name = "master")
  import SprayJsonSupport._
  import FacebookJSONProtocol._
  val pipeline: HttpRequest => Future[HttpResponse] = sendReceive
  var randomStatus = Array(
    "I didn't say half the things I said.",
    "When you come to a fork in the road, take it.",
    "You can observe a lot by just watching.",
    "It ain't over till it's over",
    "Always go to other people's funerals, otherwise they won't come to yours.",
    "No one goes there nowadays, it’s too crowded.",
    "It's like déjà vu all over again.",
    "A nickel ain't worth a dime anymore.",
    "Baseball is ninety percent mental and the other half is physical.",
    "The future ain't what it used to be.",
    "We made too many wrong mistakes.",
    "Baseball is 90% mental and the other half is physical.",
    "You better cut the pizza in four pieces because I’m not hungry enough to eat six.",
    "You wouldn’t have won if we’d beaten you.",
    "I usually take a two-hour nap from one to four.",
    "Never answer an anonymous letter.",
    "How can you think and hit at the same time?",
    "It gets late early out here.",
    "If the people don’t want to come out to the ballpark, nobody’s going to stop them.",
    "Why buy good luggage, you only use it when you travel.",
    "I’m not going to buy my kids an encyclopedia. Let them walk to school like I did.",
    "In baseball, you don’t know nothing.")
  master ! "Start"

  class Master extends Actor {
    def receive = {
      case "Start" => {
        for (x <- 1 to totalNumberOfActors) {
          var user = system.actorOf(Props(new User(x: Int)), name = x.toString)
        }
        for (x <- 1 to aggActors) {
          context.actorSelection("../" + x.toString) ! "Be Aggressive"
        }
        for (x <- (aggActors + 1) to (aggActors + modActors)) {
          context.actorSelection("../" + x.toString) ! "Be Moderate"
        }
        for (x <- (aggActors + modActors + 1) to (totalNumberOfActors)) {
          context.actorSelection("../" + x.toString) ! "Be Dormant"
        }
      }
    }
  }

  class User(identity: Int) extends Actor {
    var userID: String = ""
    var postsToBeCreated: Int = 0
    var photosToBeUploaded: Int = 0
    var albumsToBeCreated: Int = 0
    var numberOfFriends: Int = 0
    var keyPair:KeyPair = null
    val encryptionKey = "MZygpewJsCpRrfOr"
    val keySpec = new SecretKeySpec(encryptionKey.getBytes("UTF-8"), "AES")
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.ENCRYPT_MODE, keySpec)
    def receive = {
      case "Be Aggressive" => {
        postsToBeCreated = 20
        albumsToBeCreated = 3
        photosToBeUploaded = 10
        numberOfFriends = 30
        signUp
      }
      case "Be Moderate" => {
        postsToBeCreated = 10
        albumsToBeCreated = 2
        photosToBeUploaded = 10
        numberOfFriends = 20
        signUp
      }
      case "Be Dormant" => {
        postsToBeCreated = 2
        albumsToBeCreated = 1
        photosToBeUploaded = 10
        numberOfFriends = 5
        signUp
      }
      case YourUserID(id: String) => {
        userID = id
        for (i <- 1 to numberOfFriends) {
          val r = scala.util.Random
          var randomActor = r.nextInt(identity)
          context.actorSelection("../" + randomActor.toString()) ! AddMeAsFriend(userID)
        }
        for (i <- 1 to postsToBeCreated) {
          createPost
        }
        for (i <- 1 to albumsToBeCreated) {
          createAlbum
        }
      }
      case LikeMyPost(postID: String) => {
        val getPost = pipeline(Get("http://localhost:8080/posts/" + postID))
        val responseFuture = pipeline(Post("http://localhost:8080/like", Like(postID, "post", identity.toString(), userID)))
      }
      case CommentOnMyPost(postID: String) => {
        val r = scala.util.Random
        var randomCommentSelection = r.nextInt(20)
        val commentText = randomStatus(randomCommentSelection)
        val encryptedBytes = cipher.doFinal(commentText.getBytes("UTF-8"))
        val encryptedCommentText = encoder.encode(encryptedBytes)
        val cipherRsa = Cipher.getInstance("RSA")
        cipherRsa.init(Cipher.ENCRYPT_MODE, keyPair.getPublic)
        val encyrptedKeyBytes = cipherRsa.doFinal(encryptionKey.getBytes("UTF-8"))
        val encryptedKey = encoder.encode(encyrptedKeyBytes)
        val responseFuture = pipeline(Post("http://localhost:8080/comment", Comment(postID, "post", identity.toString(), userID, encryptedCommentText, encryptedKey)))
      }
      case AddMeAsFriend(id: String) => {
        if (userID != "") {
          val getProfile = pipeline(Get("http://localhost:8080/" + id + "/profile"))
          val responseFuture = pipeline(Post("http://localhost:8080/user/addFriend", AddFriend(userID, id)))
          responseFuture onComplete {
            case Success(response) => {
              val getFriends = pipeline(Get("http://localhost:8080/" + id + "/friends"))
            }
            case Failure(error) => {
              log.error(error, "Failed to Add Friend")
            }
          }
        }
      }
    }
    def signUp = {
      keyPair = generateKeyPair()
      userToKeysMap += (identity.toString() -> keyPair)
      val pubKeyEncoded = encoder.encode(keyPair.getPublic.getEncoded)
      val responseFuture = pipeline(Post("http://localhost:8080/user/add", AddUser(identity.toString(), pubKeyEncoded)))
      responseFuture onComplete {
        case Success(response) => {
          var tempID: String = ""
          tempID = tempID + response.toString()
          var idBeginning = tempID.indexOf("\"")
          var idEnding = tempID.lastIndexOf("\"")
          tempID = (tempID.substring(idBeginning + 1, idEnding))
          self ! YourUserID(tempID)
        }
        case Failure(error) => {
          log.error(error, "Failed to create User")
        }
      }
    }
    def createPost = {
      val r = scala.util.Random
      var randomPostSelection = r.nextInt(20)
      val postText = randomStatus(randomPostSelection)
      val encryptedBytes = cipher.doFinal(postText.getBytes("UTF-8"))
      val encryptedPostText = encoder.encode(encryptedBytes)
      val cipherRsa = Cipher.getInstance("RSA")
      cipherRsa.init(Cipher.ENCRYPT_MODE, keyPair.getPublic)
      val encyrptedKeyBytes = cipherRsa.doFinal(encryptionKey.getBytes("UTF-8"))
      val encryptedKey = encoder.encode(encyrptedKeyBytes)
      val responseFuture = pipeline(Post("http://localhost:8080/post/create", CreatePost(userID, encryptedPostText, encryptedKey, "F")))
      responseFuture onComplete {
        case Success(response) =>
          var postID: String = response.toString()
          var idBeginning = postID.indexOf("\"")
          var idEnding = postID.lastIndexOf("\"")
          postID = (postID.substring(idBeginning + 1, idEnding))
          for (i <- 1 to 2) {
            val r = scala.util.Random
            var askToLike = r.nextInt(totalNumberOfActors)
            context.actorSelection("../" + askToLike.toString) ! LikeMyPost(postID)
          }
          for (i <- 1 to 2) {
            val r = scala.util.Random
            var askToComment = r.nextInt(totalNumberOfActors)
            context.actorSelection("../" + askToComment.toString()) ! CommentOnMyPost(postID)
          }
        case Failure(error) =>
          log.error(error, "Failure Occured")
      }
    }
    def createAlbum = {
      val r = scala.util.Random
      var albumNumber = r.nextInt(1000)
      val responseFuture = pipeline(Post("http://localhost:8080/album/create", CreateAlbum(userID, "My Album " + albumNumber.toString(), "F")))
      responseFuture onComplete {
        case Success(response) => {
          var albumID: String = response.toString()
          var idBeginning = albumID.indexOf("\"")
          var idEnding = albumID.lastIndexOf("\"")
          albumID = (albumID.substring(idBeginning + 1, idEnding))
          val imgBytes = Files.readAllBytes(Paths.get("images/aws_cost.jpeg"))
          val imgStr = encoder.encode(imgBytes)
          val addPhoto = pipeline(Post("http://localhost:8080/photo/add", AddPhoto(userID, imgStr, albumID, "F")))
          addPhoto onComplete {
            case Success(response) => {
              println(response)
            }
            case Failure(error) => {
              log.error(error, "Failed to Add Photo")
            }
          }
        }
        case Failure(error) => {
          log.error(error, "Unable to create album.")
        }
      }
    }
    
    def generateKeyPair() : KeyPair ={
      val keygen =  KeyPairGenerator.getInstance("RSA")
      keygen.initialize(1024)
      val keyPair = keygen.generateKeyPair()
      keyPair
    }
  }
}