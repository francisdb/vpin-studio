package de.mephisto.vpin.server.archiving;

import de.mephisto.vpin.restclient.jobs.JobExecutionResult;
import de.mephisto.vpin.server.AbstractVPinServerTest;
import de.mephisto.vpin.server.archiving.adapters.TableBackupAdapter;
import de.mephisto.vpin.server.archiving.adapters.TableBackupAdapterFactory;
import de.mephisto.vpin.server.archiving.adapters.TableInstallerAdapter;
import de.mephisto.vpin.server.archiving.adapters.TableInstallerAdapterFactory;
import de.mephisto.vpin.server.archiving.adapters.vpa.VpaArchiveSource;
import de.mephisto.vpin.server.games.Game;
import de.mephisto.vpin.server.games.GameService;
import de.mephisto.vpin.server.util.ZipUtil;
import org.apache.commons.io.FilenameUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class ArchiveServiceClientTest extends AbstractVPinServerTest {

  @BeforeAll
  public void init() {
    setupSystem();
  }

  @Test
  public void testImportExport() {
    importExport(EM_TABLE_NAME);
    importExport(VPREG_TABLE_NAME);
    importExport(NVRAM_TABLE_NAME);
  }

  private void importExport(String tableName) {
    File vpaFile = new File(archiveService.getArchivesFolder(), FilenameUtils.getBaseName(tableName) + ".vpa");
    if(vpaFile.exists()) {
      vpaFile.delete();
    }

    exportTable(tableName);
    importTable(tableName);
  }

  private void importTable(String tableName) {
    String name = FilenameUtils.getBaseName(tableName);
    ArchiveDescriptor archiveDescriptor = archiveService.getArchiveDescriptor(VpaArchiveSource.DEFAULT_ARCHIVE_SOURCE_ID, name + ".vpa");
    assertNotNull(archiveDescriptor);

    TableInstallerAdapter adapter = tableInstallerAdapterFactory.createAdapter(archiveDescriptor);
    JobExecutionResult result = adapter.installTable();
    assertNull(result.getError());
  }

  private void exportTable(String tableName) {
    File vpaFile = new File(archiveService.getArchivesFolder(), FilenameUtils.getBaseName(tableName) + ".vpa");
    Game game = gameService.getGameByFilename(tableName);
    TableBackupAdapter adapter = tableBackupAdapterFactory.createAdapter(archiveService.getDefaultArchiveSourceAdapter(), game);
    JobExecutionResult msg = adapter.createBackup();
    assertNull(msg.getError());

    assertTrue(vpaFile.exists());
    assertTrue(ZipUtil.contains(vpaFile, "package-info.json"));
    assertTrue(ZipUtil.contains(vpaFile, "table-details.json"));
    assertTrue(ZipUtil.contains(vpaFile, FilenameUtils.getBaseName(tableName) + ".directb2s"));
    assertTrue(ZipUtil.contains(vpaFile, tableName));

    archiveService.invalidateCache();
  }
}
