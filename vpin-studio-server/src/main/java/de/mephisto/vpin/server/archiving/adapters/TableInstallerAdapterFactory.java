package de.mephisto.vpin.server.archiving.adapters;

import de.mephisto.vpin.restclient.ArchiveType;
import de.mephisto.vpin.server.archiving.ArchiveDescriptor;
import de.mephisto.vpin.server.archiving.adapters.vpa.TableInstallerAdapterVpa;
import de.mephisto.vpin.server.archiving.adapters.vpbm.TableInstallerAdapterVpbm;
import de.mephisto.vpin.server.archiving.adapters.vpbm.VpbmService;
import de.mephisto.vpin.server.games.GameService;
import de.mephisto.vpin.server.popper.PinUPConnector;
import de.mephisto.vpin.server.system.SystemService;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TableInstallerAdapterFactory {

  @Autowired
  private SystemService systemService;

  @Autowired
  private PinUPConnector pinUPConnector;

  @Autowired
  private VpbmService vpbmService;

  @Autowired
  private GameService gameService;

  public TableInstallerAdapter createAdapter(@NonNull ArchiveDescriptor archiveDescriptor) {
    ArchiveType archiveType = systemService.getArchiveType();

    switch (archiveType) {
      case VPA: {
        return new TableInstallerAdapterVpa(systemService, gameService, pinUPConnector, archiveDescriptor);
      }
      case VPBM: {
        return new TableInstallerAdapterVpbm(gameService, vpbmService, archiveDescriptor);
      }
      default: {
        throw new UnsupportedOperationException("Unkown archive type " + archiveType);
      }
    }
  }
}
