package belfius.bpms.processes.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PseudoColumnUsage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.drools.core.command.runtime.process.SetProcessInstanceVariablesCommand;
import org.drools.core.time.impl.PseudoClockScheduler;
import org.jbpm.process.audit.JPAAuditLogService;
import org.jbpm.process.audit.NodeInstanceLog;
import org.jbpm.runtime.manager.impl.deploy.DeploymentDescriptorManagerUtil;
import org.jbpm.test.JbpmJUnitBaseTestCase;
import org.junit.Before;
import org.kie.api.builder.helper.KieModuleDeploymentConfig;
import org.kie.api.io.Resource;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.task.TaskService;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.Task;
import org.kie.api.task.model.TaskSummary;
import org.kie.internal.runtime.conf.DeploymentDescriptor;
import org.kie.internal.runtime.conf.NamedObjectModel;
import org.kie.internal.runtime.conf.ObjectModel;
import org.kie.internal.runtime.manager.context.ProcessInstanceIdContext;
import org.kie.internal.runtime.manager.deploy.DeploymentDescriptorIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import belfius.bpms.processes.test.model.ProcessNode;
import belfius.bpms.processes.test.model.ProcessNode.NodeType;

public class BelfiusBusinessProcessBaseTestCase extends JbpmJUnitBaseTestCase {

	private static final Logger logger = LoggerFactory.getLogger(BelfiusBusinessProcessBaseTestCase.class);

	private TaskService taskService = null;
	private RuntimeEngine runtimeEngine = null;
	private KieSession ksession = null;
	private Map<String, ResourceType> definitionsMap = null;
	private Map<Long, WorkItem> activeWorkItemMap = null;
	private JPAAuditLogService auditLogService = null;

	/**
	 * Allows to load multiple process definition files (.bpmn)
	 * @param setupDataSource
	 * @param sessionPersistence
	 * @param processDefinitionFileLocation .bpmn files to be loaded. The root directory is src/main/resource.
	 */
	public BelfiusBusinessProcessBaseTestCase(boolean setupDataSource, boolean sessionPersistence, String... processDefinitionFileLocation) {
		super(setupDataSource, sessionPersistence);

		definitionsMap = new HashMap<String, ResourceType>();
		
		logger.debug("Loading assets with process and decision definitions...");
		
		for (String definition : processDefinitionFileLocation) {
			logger.debug(definition + " loaded");
			if(definition.endsWith(".dmn"))
				definitionsMap.put(definition, ResourceType.DMN);
			else if(definition.endsWith(".bpmn") || definition.endsWith(".bpmn2"))
				definitionsMap.put(definition, ResourceType.BPMN2);
		}
	}

	public BelfiusBusinessProcessBaseTestCase(boolean setupDataSource, String persistenceUnitName, boolean sessionPersistence, String... processDefinitionFileLocation) {
		super(setupDataSource, sessionPersistence, persistenceUnitName);

		definitionsMap = new HashMap<String, ResourceType>();
		
		logger.debug("Loading assets with process and decision definitions...");
		
		for (String definition : processDefinitionFileLocation) {
			logger.debug(definition + " loaded");
			if(definition.endsWith(".dmn"))
				definitionsMap.put(definition, ResourceType.DMN);
			else if(definition.endsWith(".bpmn") || definition.endsWith(".bpmn2"))
				definitionsMap.put(definition, ResourceType.BPMN2);
		}
	}


	//////////////////////////////////////
	// Runtime environment setup operations
	//////////////////////////////////////	

	@Before
	public void buildRuntimeEnvironment () throws IOException {
		System.setProperty("drools.clockType", "pseudo");
		
		logger.debug("Creating Runtime Environment...");
		
		createRuntimeManager(Strategy.SINGLETON, definitionsMap);
		runtimeEngine = getRuntimeEngine(ProcessInstanceIdContext.get());
		taskService = runtimeEngine.getTaskService();
		ksession = runtimeEngine.getKieSession();
		registerWorkItemHandlers();
		activeWorkItemMap = new HashMap<Long, WorkItem>();
		auditLogService = (JPAAuditLogService)runtimeEngine.getAuditService();
		
		logger.debug("Runtime Environment successfully created.");
	}
	
	/**
	 * Registers test WorkItemHandlers for each name provided.
	 * @param workItemNames
	 */
	protected void registerWorkItemHandlers() throws IOException  {

		logger.debug("Registering WorkItemHandlers...");
		
		DeploymentDescriptor deploymentDescriptor = null;
		
		InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("META-INF/kie-deployment-descriptor.xml");
		deploymentDescriptor = DeploymentDescriptorIO.fromXml(inputStream);
		
		List<NamedObjectModel> workItemHandlerList = deploymentDescriptor.getWorkItemHandlers();
		
		for (Iterator<NamedObjectModel> iterator = workItemHandlerList.iterator(); iterator.hasNext();) {
			NamedObjectModel wihdef = iterator.next();
			ksession.getWorkItemManager().registerWorkItemHandler(wihdef.getName(), getTestWorkItemHandler());
			logger.debug("WorkItemHandler for Service Task " + wihdef.getName() + " successfully registered.");
		}
	}
	
	
	//////////////////////////////////////
	// Process Instance operations
	//////////////////////////////////////	

	/**
	 * Starts a new process instance with inputs.
	 * @param processId Process Definition ID
	 * @param processInputs Process input variables
	 * @return
	 */
	protected long startProcessInstance(String processId, Map<String, Object> processInputs) {

		ProcessInstance processInstance = ksession.startProcess(processId, processInputs);
		return processInstance.getId();
	}

	/**
	 * Inserts a new variable in the process instance, or updates it if it already exists.
	 * @param processInstanceId
	 * @param variableId
	 * @param variableValue
	 */
	protected void setProcessVariable(Long processInstanceId, String variableId, Object variableValue) {

		Map<String, Object> processUpdatedVariables = new HashMap<String, Object>();
		processUpdatedVariables.put(variableId, variableValue);

		SetProcessInstanceVariablesCommand command = new SetProcessInstanceVariablesCommand();
		command.setProcessInstanceId(processInstanceId);
		command.setVariables(processUpdatedVariables);
		ksession.execute(command);
	}

	/**
	 * Sends a signal to a specific event to a specific process instance.
	 * @param signalId
	 * @param inputs
	 * @param processInstanceId
	 */
	protected void signalEvent(String signalId, Map<String, Object> signalOutputs, Long processInstanceId) {
		setProcessVariables(processInstanceId, signalOutputs);
		ksession.signalEvent(signalId, null, processInstanceId);
	}

	/**
	 * Inserts a set of variables in the process instance, or updates them if they already exist.
	 * @param processInstanceId
	 * @param processUpdatedVariables
	 */
	protected void setProcessVariables(Long processInstanceId, Map<String, Object> processUpdatedVariables) {

		SetProcessInstanceVariablesCommand command = new SetProcessInstanceVariablesCommand();
		command.setProcessInstanceId(processInstanceId);
		command.setVariables(processUpdatedVariables);
		ksession.execute(command);
	}
	
	/**
	 * Retrieves the value of a process variable by its id.
	 * @param processInstanceId
	 * @param variableId
	 */
	protected Object getProcessVariable(Long processInstanceId, String variableId) {

		return getVariableValue(variableId, processInstanceId, ksession);
	}
	
	
	//////////////////////////////////////
	// Timer Operations
	//////////////////////////////////////
	
	/**
	 * Simulates a change in the current date. Useful to simulate timer expirations in a process instance.
	 * @param time The amount of time to be advanced.
	 * @param timeUnit The time unit (days, hours, minutes...)
	 */
	protected void advanceTime(long time, TimeUnit timeUnit) {
		PseudoClockScheduler sessionClock = ksession.getSessionClock();
		sessionClock.advanceTime(time, timeUnit);
	}
	

	//////////////////////////////////////
	// Service Task Assertions
	//////////////////////////////////////

	/**
	 * Asserts Service Task Completed
	 * @param ksession Active KieSession
	 * @param workItem The active WorkItem whose name, status (active), inputs and outputs will be asserted.
	 * @param activityName The name of the activity that is expected to be asserted.
	 * @param inputs Input data to be asserted in the WorkItem.
	 * @param outputs Output data to be used when completing the WorkItem.
	 */
	public void assertServiceTaskCompleted(Long processInstanceId, ProcessNode processNode, Map<String, Object> inputs, Map<String, Object> outputs) {

		// Validating that the workitem is a service task node.
		if(!processNode.getNodeType().equals(NodeType.SERVICE_TASK))
			throw new IllegalArgumentException("Wrong node type. It must be NodeType.SERVICE_TASK");
		
		// This will get active TestWorkItemHandler.
		TestWorkItemHandler testWorkItemHandler = getTestWorkItemHandler();
		
		WorkItem workItem = null;
		
		// This will get all active WorkItems and it will clear them from the jBPM runtime memory.
		List<WorkItem> activeWorkItemsList = testWorkItemHandler.getWorkItems();
		
		// We're required to save them in our own map to allow parallel paths to be asserted in individual steps.
		for (WorkItem activeWorkItem : activeWorkItemsList) {
			activeWorkItemMap.put(activeWorkItem.getId(), activeWorkItem);
		}
		
		// Now we try to find the one we want to assert.
		for (Map.Entry<Long, WorkItem> workItemEntry : activeWorkItemMap.entrySet()) {
			WorkItem potentialWorkItem = workItemEntry.getValue();
			
			if(potentialWorkItem.getName().equals(processNode.getNodeId()))
				workItem = potentialWorkItem;
		}
		
		// Validating that the workitem has been activated.
		assertNotNull(workItem);

		// Validating that the workitem has been triggered.
		assertNodeTriggered(processInstanceId, processNode.getNodeName());

		// Validating inputs.
		if(inputs != null) {
			for (Map.Entry<String, Object> inputVariable : inputs.entrySet()) {
				assertEquals(inputVariable.getValue(), workItem.getParameter(inputVariable.getKey()));
			}
		}

		// Completing the workitem.
		ksession.getWorkItemManager().completeWorkItem(workItem.getId(), outputs);
		activeWorkItemMap.remove(workItem.getId());
	}

	public void assertNumberOfActiveServiceTasks(int expectedActiveServiceTasks) {
		assertEquals(activeWorkItemMap.size(), expectedActiveServiceTasks);
	}


	//////////////////////////////////////
	// User Task Assertions
	//////////////////////////////////////

	/**
	 * Asserts User Task Active
	 * @param processInstanceId The process instance which the user task belongs to.
	 * @param userTaskName The label of the user task in the BPMN diagram.
	 */
	public void assertUserTaskActive(Long processInstanceId, String userTaskName) {
		assertNodeActive(processInstanceId, ksession, userTaskName);
	}

	
	
	/**
	 * Asserts User Task Completed
	 * @param processInstanceId The process instance which the user task belongs to.
	 * @param userTaskName The label of the user task in the BPMN diagram.
	 * @param userId The performer of the user task.
	 * @param inputs Input data to be asserted in the WorkItem.
	 * @param outputs Output data to be used when completing the WorkItem.
	 */
	public void assertUserTaskCompleted(Long processInstanceId, String userTaskName, String userId, Map<String, Object> inputs, Map<String, Object> outputs) {

		assertUserTaskActive(processInstanceId, userTaskName);
		
		// We've found the user task in the user task list.
		List<TaskSummary> taskList = taskService.getTasksAssignedAsPotentialOwner(userId, "en-uk");
		TaskSummary targetUserTask = null;
		Task updatedTask = null;
		Status taskStatus = null;

		for (TaskSummary taskSummary : taskList) {
			if(userTaskName.equals(taskSummary.getName())) {
				targetUserTask = taskSummary;
				break;
			}
		}

		// We've found the user task in the user task list.
		assertNotNull(targetUserTask);

		updatedTask = taskService.getTaskById(targetUserTask.getId());
		taskStatus = updatedTask.getTaskData().getStatus();
		
		// The task must be ready to be handled.
		assertNotEquals(taskStatus, Status.Completed);
		assertNotEquals(taskStatus, Status.Created);
		assertNotEquals(taskStatus, Status.Error);
		assertNotEquals(taskStatus, Status.Failed);
		assertNotEquals(taskStatus, Status.Obsolete);
		assertNotEquals(taskStatus, Status.Suspended);
		assertNotEquals(taskStatus, Status.Exited);

		// Claim the task if necessary
		if(taskStatus.equals(Status.Ready)) {
			taskService.claim(targetUserTask.getId(), userId);
			updatedTask = taskService.getTaskById(targetUserTask.getId());
			taskStatus = updatedTask.getTaskData().getStatus(); 
			assertEquals(taskStatus, Status.Reserved);
		}
		
		// Complete the task.			
		taskService.start(targetUserTask.getId(), userId);
		taskService.complete(targetUserTask.getId(), userId, outputs);

		// Check that the task is completed.
		updatedTask = taskService.getTaskById(targetUserTask.getId());
		taskStatus = updatedTask.getTaskData().getStatus();
		assertEquals(taskStatus, Status.Completed);
	}


	//////////////////////////////////////
	// Debug operations
	//////////////////////////////////////
	
	/**
	 * Prints the active node instances given a process instance id
	 * @param processInstanceId
	 */
	public void printActiveNodes (Long processInstanceId) {
		List<NodeInstanceLog> nodeInstanceLogList = auditLogService.findNodeInstances(processInstanceId);
		Map<String, NodeInstanceLog> activeNodes = new HashMap<String, NodeInstanceLog>();
		
		for (NodeInstanceLog nodeInstanceLog : nodeInstanceLogList) {
			if(nodeInstanceLog.getType().equals(NodeInstanceLog.TYPE_ENTER)) {
				activeNodes.put(nodeInstanceLog.getNodeId(), nodeInstanceLog);
			}
			else {
				activeNodes.remove(nodeInstanceLog.getNodeId());
			}
		}
		
		Collection<NodeInstanceLog> nodeInstanceLogCollection = activeNodes.values();
		for (NodeInstanceLog nodeInstanceLog : nodeInstanceLogCollection) {
			logger.info(nodeInstanceLog.getNodeType() + " - " + nodeInstanceLog.getNodeName());
		}
		
	}
	
	/**
	 * Prints the completed node instances given a process instance id
	 * @param processInstanceId
	 */
	public void printCompletedNodes (Long processInstanceId) {		
		List<NodeInstanceLog> completedNodeInstanceLogList = auditLogService.findNodeInstances(processInstanceId);
		
		for (NodeInstanceLog nodeInstanceLog : completedNodeInstanceLogList) {
			if(nodeInstanceLog.getType().equals(NodeInstanceLog.TYPE_EXIT)) {
				logger.info(nodeInstanceLog.getNodeType() + " - " + nodeInstanceLog.getNodeName());
			}
		}
	}
	
	/**
	 * Prints the full ordered nodeInstance log for a processInstanceId. It prints both node starts and completions.
	 * @param processInstanceId
	 */
	public void printNodeInstanceList (Long processInstanceId) {		
		List<NodeInstanceLog> completedNodeInstanceLogList = auditLogService.findNodeInstances(processInstanceId);
		
		for (NodeInstanceLog nodeInstanceLog : completedNodeInstanceLogList) {
			logger.info(nodeInstanceLog.getNodeType() + " - " + nodeInstanceLog.getNodeName() + " - " + (nodeInstanceLog.getType() == 0 ? "Entered" : "Completed"));
		}
	}
}
