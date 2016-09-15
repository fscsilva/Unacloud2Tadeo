package communication.send;

import utils.VariableManager;

import com.losandes.enums.VirtualMachineExecutionStateEnum;
import com.losandes.utils.UnaCloudConstants;

import communication.UnaCloudDataSenderUDP;
import communication.messages.udp.UDPMessageLogPM;
import communication.messages.udp.UDPMessageStatePM;
import communication.messages.udp.UDPMessageStateVM;

/**
 * Singleton class to send message to server using UDP protocol
 * @author CesarF
 *
 */
public class UDPCommunicator {

	private UnaCloudDataSenderUDP sender;
	
	private static UDPCommunicator instance;
	
	public static UDPCommunicator getInstance(){
		if(instance == null)instance = new UDPCommunicator();
		return instance;
	}
	
	private UDPCommunicator() {
		sender = new UnaCloudDataSenderUDP();
	}
	
	/**
	 * Push info by UDP protocol to server port for physical machine reports
	 * @param params
	 * @return true if message was sent, false in case not
	 * @throws Exception 
	 */
	public boolean pushInfoPM(String hostName, String userName, Long[] executions) throws Exception{
		String serverIP=VariableManager.getInstance().getGlobal().getStringVariable(UnaCloudConstants.CONTROL_SERVER_IP);
		int serverPort =VariableManager.getInstance().getGlobal().getIntegerVariable(UnaCloudConstants.CONTROL_MANAGE_PM_PORT);
		UDPMessageStatePM message = new UDPMessageStatePM(serverIP, serverPort, hostName, userName, executions);
		
		return sender.sendMessage(message);
	}
	
	/**
	 * Push info by UDP protocol to server port for virtual machines reports
	 * @param params
	 * @return Starts and configures a virtual machine. this method must be used by other methods to configure, start and schedule a virtual machine execution
	 * @throws Exception 
	 */
	public boolean pushInfoVM(String hostName, long virtualMachineCode, VirtualMachineExecutionStateEnum state, String messageExecution) throws Exception{		
		String serverIP=VariableManager.getInstance().getGlobal().getStringVariable(UnaCloudConstants.CONTROL_SERVER_IP);
		int serverPort =VariableManager.getInstance().getGlobal().getIntegerVariable(UnaCloudConstants.CONTROL_MANAGE_VM_PORT);
		UDPMessageStateVM message = new UDPMessageStateVM(serverIP, serverPort, hostName, virtualMachineCode, state, messageExecution);
		return sender.sendMessage(message);
	}
	
	/**
	 * Push Info by UDP Protocol to server port for Log in Physical Machines
	 * @param type UDPMessageEnum
	 * @param hostName Sender
	 * @param component
	 * @param logMessage
	 * @return true if message was sent, false in case not
	 * @throws Exception
	 */
	public boolean pushInfoLogPM(String hostName, String component, String logMessage) throws Exception {
		String serverIP=VariableManager.getInstance().getGlobal().getStringVariable(UnaCloudConstants.CONTROL_SERVER_IP);
		int serverPort =VariableManager.getInstance().getGlobal().getIntegerVariable(UnaCloudConstants.CONTROL_MANAGE_VM_PORT);
		UDPMessageLogPM message = new UDPMessageLogPM(serverIP, serverPort, hostName, component, logMessage);
		return sender.sendMessage(message);
	}
	
}