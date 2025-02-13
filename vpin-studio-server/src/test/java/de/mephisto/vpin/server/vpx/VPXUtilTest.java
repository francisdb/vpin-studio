package de.mephisto.vpin.server.vpx;

import org.junit.jupiter.api.Test;

import java.io.File;

import static de.mephisto.vpin.server.AbstractVPinServerTest.TABLE_NAMES;
import static org.junit.jupiter.api.Assertions.assertNotNull;


public class VPXUtilTest {

  @Test
  public void testScript() {
    for (String tableName : TABLE_NAMES) {
      File table = new File("../testsystem/vPinball/VisualPinball/Tables/" + tableName);
      String script = VPXUtil.readScript(table);
      assertNotNull(script);
    }
  }
}
