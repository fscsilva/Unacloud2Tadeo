package uniandes.unacloud.file.net.task;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.Socket;
import java.sql.Connection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import uniandes.unacloud.common.enums.ExecutionProcessEnum;
import uniandes.unacloud.common.net.tcp.AbstractTCPSocketProcessor;
import uniandes.unacloud.common.utils.FileConverter;
import uniandes.unacloud.share.db.ExecutionManager;
import uniandes.unacloud.share.db.entities.ExecutionEntity;
import uniandes.unacloud.share.enums.ExecutionStateEnum;
import uniandes.unacloud.share.enums.ImageEnum;
import uniandes.unacloud.utils.file.FileProcessor;
import uniandes.unacloud.file.FileManager;
import uniandes.unacloud.file.db.UserManager;
import uniandes.unacloud.file.db.ImageFileManager;
import uniandes.unacloud.file.db.entities.UserEntity;
import uniandes.unacloud.file.db.entities.ImageFileEntity;
import uniandes.unacloud.file.net.torrent.TorrentTracker;

/**
 * This class receives files from agent when user requests to save image in server.
 * @author CesarF
 *
 */
public class FileReceiverTask extends AbstractTCPSocketProcessor {
	
	/**
	 * Creates a new file receiver task
	 * @param s socket to process task
	 */
	public FileReceiverTask(Socket s) {
		super(s);
		System.out.println("Attending " + s.getRemoteSocketAddress());
	}
	

	@Override
	public void processMessage(Socket s) throws Exception {
		
		FileConverter zip = null;
		ImageFileEntity image = null;
		String mainFolder = null;
		UserEntity user = null;
		Long execution = null;
		Long fileSize = null;
		
		//Receiving file from agent
		try (Socket ss = s; DataInputStream is = new DataInputStream(s.getInputStream());) {
			
			execution = is.readLong();
			String token= is.readUTF();
			fileSize = is.readLong();
			System.out.println("\tRequest " + execution + " - " + token);
			
			try (Connection con = FileManager.getInstance().getDBConnection();) {
				image = ImageFileManager.getImageWithFile(token, con);
				if (image != null)
					user = UserManager.getUser(image.getOwner().getId(), con);
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			System.out.println("\tImage requested " + image);	
			
			if (image != null) {
				mainFolder = image.getRepository().getRoot() + image.getName() + "_" + user.getUsername() + File.separator;
				try (ZipInputStream zis = new ZipInputStream(is)) {
					
					System.out.println("\tZip open");
					final byte[] buffer = new byte[1024 * 100];
					ZipEntry entry = zis.getNextEntry();
					System.out.println("\t\tFile: " + entry.getName());
					new File(mainFolder).mkdirs();
					File fileFromAgent = new File(mainFolder + entry.getName());
					try (FileOutputStream fos = new FileOutputStream(fileFromAgent)) {
						for (int n; (n = zis.read(buffer)) != -1;)
							fos.write(buffer, 0, n);							
					}	
					System.out.println("Reception finished " + fileFromAgent);
					zip = new FileConverter(fileFromAgent);
					zis.closeEntry();				
							
				} catch (Exception e) {		
				    e.printStackTrace();
				}	
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		if (image != null && execution != null) {
			// Save file in path
			String message = null;
			
			if (zip != null) {
				try {					
					//Announce in torrent
					TorrentTracker.getInstance().publishFile(zip);
					message = "Copying process has been successful";
				} catch (Exception e) {
					e.printStackTrace();
					message = e.getMessage();
				}
			}
			else {
				FileProcessor.deleteFileSync(mainFolder);	
				message = "Error receiving file from agent. Check logs";		
			}
			
			//Modifying data
			try (Connection con = FileManager.getInstance().getDBConnection();) {
				if (zip != null) {
					ImageFileManager.setImageFile(new ImageFileEntity(image.getId(), ImageEnum.AVAILABLE, null, null, null, null, fileSize, zip.getExecutableFile().getAbsolutePath(), null, null), false, con, true);
					System.out.println("Status changed, process closed");
					ExecutionEntity exe = new ExecutionEntity(execution, 0, 0, null, null, ExecutionProcessEnum.SUCCESS, null, message);
					ExecutionManager.updateExecution(exe, ExecutionStateEnum.COPYING, con);
				} 
				else {						
					System.out.println("Error in process, all files must be deleted");
					ExecutionEntity exe = new ExecutionEntity(execution, 0, 0, null, null, ExecutionProcessEnum.FAIL, null, message);
					ExecutionManager.updateExecution(exe, ExecutionStateEnum.COPYING, con);
				}
				
			} catch (Exception e) {
				e.printStackTrace();
			}
		}	
	
	}
}