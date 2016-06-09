package unacloud.share.queue.messages;

import org.json.JSONObject;

import unacloud.share.enums.QueueMessageType;
import unacloud.share.queue.QueueMessage;


/**
 * Class used to represent the message to Create Copy from Public 
 * @author cdsbarrera
 * 
 */
public class MessageCreateCopyFromPublic extends QueueMessage{
	
	private final static String TAG_ID_IMAGE = "id_image";
	private final static String TAG_ID_PUBLIC_IMAGE = "id_public_image";
	
	public MessageCreateCopyFromPublic(String requester, long idImage, long idPublicImage){
		super(requester);
		this.setType(QueueMessageType.CREATE_COPY_FROM_PUBLIC);
		
		JSONObject temp = this.getMessageContent();
		
		temp.put(TAG_ID_IMAGE, idImage);
		temp.put(TAG_ID_PUBLIC_IMAGE, idPublicImage);
		
		this.setMessageContent(temp);
	}

	/**
	 * Return the Id of Image
	 * @return Long Id Image
	 */
	public Long getIdImage() {
		JSONObject temp = this.getMessageContent();
		return temp.getLong(TAG_ID_IMAGE);
	}
	
	/**
	 * Return the Id of Public Image
	 * @return Long Id Public Image
	 */
	public Long getIdPublicImage() {
		JSONObject temp = this.getMessageContent();
		return temp.getLong(TAG_ID_PUBLIC_IMAGE);
	}
}
