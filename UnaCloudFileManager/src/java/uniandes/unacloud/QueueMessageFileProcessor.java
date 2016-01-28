package uniandes.unacloud;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.io.FileUtils;

import com.losandes.utils.Constants;

import db.HypervisorManager;
import db.RepositoryManager;
import db.VirtualImageManager;
import queue.QueueMessage;
import queue.QueueReader;
import unacloud.entities.HypervisorEntity;
import unacloud.entities.RepositoryEntity;
import unacloud.entities.VirtualMachineImageEntity;
import unacloud.enums.VirtualMachineImageEnum;
import uniandes.unacloud.db.UserManager;
import uniandes.unacloud.db.VirtualMachineImageManager;
import uniandes.unacloud.db.entities.UserEntity;
import uniandes.unacloud.db.entities.VirtualImageFileEntity;

/**
 * Class to process messages sent to manage files
 * @author Cesar
 *
 */
public class QueueMessageFileProcessor implements QueueReader{
	
	public static void main(String[] args) {
		String h = "hola.vc";
		if(h.matches(".*.vc"))System.out.println("p");
	}
	
	private ExecutorService threadPool=Executors.newFixedThreadPool(5);

	@Override
	public void processMessage(QueueMessage message) {
		System.out.println("Receive message "+message.getType());
		switch (message.getType()) {
		case CREATE_PUBLIC_IMAGE:	
			createPublicImage(message);
			break;
		case CREATE_COPY_FROM_PUBLIC:			
			createPrivateImage(message);
			break;
		case DELETE_IMAGE:
			deleteImage(message);
			break;
		case DELETE_PUBLIC_IMAGE:	
			deletePublicImage(message);
			break;
		case DELETE_USER:		
			deleteUser(message);
			break;
		default:
			break;
		}
	}
	

	/**
	 * Copy a current private image to a public folder
	 * @param message
	 */
	private void createPublicImage(QueueMessage message) {
		threadPool.submit(new MessageProcessor(message) {			
			@Override
			protected void processMessage(QueueMessage message) throws Exception{
				Long imageId = Long.parseLong(message.getMessageParts()[0]);
				VirtualImageFileEntity image = VirtualMachineImageManager.getVirtualImageWithFile(imageId, VirtualMachineImageEnum.IN_QUEUE,false);
				if(image!=null){
					if(!image.isPublic()){
						RepositoryEntity main = RepositoryManager.getRepositoryByName(Constants.MAIN_REPOSITORY);
						File file = new File(main.getRoot()+Constants.TEMPLATE_PATH+File.separator+image.getName());
						if(!file.exists()){
							File folder = new File(image.getMainFile().substring(0, image.getMainFile().lastIndexOf(File.separator.toString())));
							for(File imagefile: folder.listFiles()){
								File newFile = new File(main.getRoot()+Constants.TEMPLATE_PATH+File.separator+image.getName()+File.separator+imagefile.getName());
								FileUtils.copyFile(imagefile, newFile);
							}
							VirtualMachineImageManager.setVirtualMachine(new VirtualImageFileEntity(image.getId(), VirtualMachineImageEnum.AVAILABLE, null, null, true, null, null, null));
						}else{
							VirtualImageManager.setVirtualMachine(new VirtualMachineImageEntity(image.getId(), null, null, VirtualMachineImageEnum.AVAILABLE, null));
						}
					}else{
						VirtualImageManager.setVirtualMachine(new VirtualMachineImageEntity(image.getId(), null, null, VirtualMachineImageEnum.AVAILABLE, null));
					}
				}
			}			
			@Override
			protected void processError(Exception e) {
				//TODO notification
			}
		});	
	}
	
	/**
	 * Creates a private image copy from a public one
	 * @param message
	 */
	private void createPrivateImage(QueueMessage message){
		threadPool.submit(new MessageProcessor(message) {			
			@Override
			protected void processMessage(QueueMessage message) throws Exception{
				Long imageId = Long.parseLong(message.getMessageParts()[0]);
				Long publicImageId = Long.parseLong(message.getMessageParts()[1]);
				VirtualImageFileEntity publicImage = VirtualMachineImageManager.getVirtualImageWithFile(publicImageId, VirtualMachineImageEnum.AVAILABLE, false);
				VirtualImageFileEntity privateImage = VirtualMachineImageManager.getVirtualImageWithFile(imageId, VirtualMachineImageEnum.IN_QUEUE, true);
				if(publicImage!=null&&privateImage!=null){
					if(publicImage.isPublic()){
						RepositoryEntity main = RepositoryManager.getRepositoryByName(Constants.MAIN_REPOSITORY);
						File folder = new File(main.getRoot()+Constants.TEMPLATE_PATH+File.separator+publicImage.getName());
						if(folder.exists()){
							List<HypervisorEntity>hypervisors = HypervisorManager.getAllHypervisors();
							String regex = "";
							for(HypervisorEntity hv:hypervisors)regex+=".*"+hv.getExtension()+(hypervisors.indexOf(hv)<hypervisors.size()-1?"|":"");
							UserEntity user = UserManager.getUser(privateImage.getOwner().getId());
							String mainFile = null;
							for(File imagefile: folder.listFiles()){
								File newFile = new File(privateImage.getRepository().getRoot()+privateImage.getName()+"_"+user.getUsername()+File.separator+imagefile.getName());
								FileUtils.copyFile(imagefile, newFile);
								if(imagefile.getName().matches(regex)){
									mainFile = user.getRepository().getRoot()+privateImage.getName()+"_"+user.getUsername()+File.separator+newFile.getName();
								}							
							}
							VirtualMachineImageManager.setVirtualMachine(new VirtualImageFileEntity(privateImage.getId(), VirtualMachineImageEnum.AVAILABLE, null, null, null, null, mainFile, null));
						}else{
							VirtualMachineImageManager.setVirtualMachine(new VirtualImageFileEntity(publicImage.getId(), null, null, null, false, null, null, null));
							VirtualImageManager.deleteVirtualMachineImage(new VirtualMachineImageEntity(privateImage.getId(), null, null, VirtualMachineImageEnum.IN_QUEUE, null));
						}
					}else{
						VirtualImageManager.deleteVirtualMachineImage(new VirtualMachineImageEntity(privateImage.getId(), null, null, VirtualMachineImageEnum.IN_QUEUE, null));
					}
				}else{
					if(privateImage!=null){
						VirtualImageManager.deleteVirtualMachineImage(new VirtualMachineImageEntity(privateImage.getId(), null, null, VirtualMachineImageEnum.IN_QUEUE, null));
					}
				}
			}			
			@Override
			protected void processError(Exception e) {
				//TODO notification
			}
		});	
	}
	
	/**
	 * Delete files and image entity
	 * @param message
	 */
	private void deleteImage(QueueMessage message){
		threadPool.submit(new MessageProcessor(message) {			
			@Override
			protected void processMessage(QueueMessage message) throws Exception{
				Long imageId = Long.parseLong(message.getMessageParts()[0]);
				VirtualImageFileEntity image = VirtualMachineImageManager.getVirtualImageWithFile(imageId, VirtualMachineImageEnum.IN_QUEUE,false);
				if(image!=null){
					try {
						File file = new File(image.getMainFile());
						if(file!=null)file.getParentFile().delete();
					} catch (Exception e) {
						System.err.println("No delete original image files "+image.getMainFile());
					}
					try {
						if(image.isPublic()){
							RepositoryEntity main = RepositoryManager.getRepositoryByName(Constants.MAIN_REPOSITORY);
							File folder = new File(main.getRoot()+Constants.TEMPLATE_PATH+File.separator+image.getName()+File.separator);						
							if(folder.exists())folder.delete();	
						}
					} catch (Exception e) {
						System.err.println("No delete public copy files "+Constants.TEMPLATE_PATH+File.separator+image.getName()+File.separator);
					}					
					VirtualImageManager.deleteVirtualMachineImage(new VirtualMachineImageEntity(image.getId(), null, null, VirtualMachineImageEnum.IN_QUEUE, null));
				}
			}			
			@Override
			protected void processError(Exception e) {
				//TODO notification
			}
		});	
	}
	
	/**
	 * Delete files by user, image entities and user entity
	 * @param message
	 */
	private void deleteUser(QueueMessage message) {
		threadPool.submit(new MessageProcessor(message) {			
			@Override
			protected void processMessage(QueueMessage message) throws Exception{
				Long userId = Long.parseLong(message.getMessageParts()[0]);
				
				//TODO complete process
			}			
			@Override
			protected void processError(Exception e) {
				//TODO notification
			}
		});	
	}

	/**
	 * Delete files from a public image
	 * @param message
	 */
	private void deletePublicImage(QueueMessage message) {
		threadPool.submit(new MessageProcessor(message) {			
			@Override
			protected void processMessage(QueueMessage message) throws Exception{
				Long imageId = Long.parseLong(message.getMessageParts()[0]);
				VirtualImageFileEntity image = VirtualMachineImageManager.getVirtualImageWithFile(imageId, VirtualMachineImageEnum.IN_QUEUE,false);
				if(image!=null){
					if(image.isPublic()){
						RepositoryEntity main = RepositoryManager.getRepositoryByName(Constants.MAIN_REPOSITORY);
						File folder = new File(main.getRoot()+Constants.TEMPLATE_PATH+File.separator+image.getName()+File.separator);						
						if(folder.exists())folder.delete();	
					}
					VirtualImageManager.setVirtualMachine(new VirtualMachineImageEntity(image.getId(), null, null, VirtualMachineImageEnum.AVAILABLE, null));
				}
			}			
			@Override
			protected void processError(Exception e) {
				//TODO notification
			}
		});	
	}

}
