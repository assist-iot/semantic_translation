package eu.assist_iot.ipsm

package object core {

  def md5Hash(text: String): String = {
    import java.security.MessageDigest
    val md5 = MessageDigest.getInstance("MD5")
    md5.digest(text.getBytes())
      .map(0xFF & _)
      .map { "%02x".format(_) }
      .foldLeft(""){_ + _}
  }

}
