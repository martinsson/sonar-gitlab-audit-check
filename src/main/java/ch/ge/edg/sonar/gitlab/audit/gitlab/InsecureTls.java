package ch.ge.edg.sonar.gitlab.audit.gitlab;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/** --insecure : un contexte TLS qui accepte tout certificat, pour une instance auto-signée. */
final class InsecureTls {

  private InsecureTls() {}

  static SSLContext trustEverything() throws GeneralSecurityException {
    SSLContext ctx = SSLContext.getInstance("TLS");
    ctx.init(null, new TrustManager[] {new TrustAll()}, new SecureRandom());
    return ctx;
  }

  /** Ne vérifie rien : c'est ce que l'option demande. */
  private static final class TrustAll implements X509TrustManager {

    @Override
    public void checkClientTrusted(X509Certificate[] c, String a) {
      // tout certificat client est accepté
    }

    @Override
    public void checkServerTrusted(X509Certificate[] c, String a) {
      // tout certificat serveur est accepté
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }
  }
}
