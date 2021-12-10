package belfius.bpms.processes.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.core.command.runtime.process.SetProcessInstanceVariablesCommand;
import org.jbpm.test.JbpmJUnitBaseTestCase;
import org.junit.Before;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.task.TaskService;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.Task;
import org.kie.api.task.model.TaskSummary;
import org.kie.internal.runtime.manager.context.ProcessInstanceIdContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import belfius.bpms.processes.test.model.ProcessNode;
import belfius.bpms.processes.test.model.ProcessNode.NodeType;

public class BelfiusBusinessProcessBaseTestCase extends JbpmJUnitBaseTestCase {

	private static final Logger logger = LoggerFactory.getLogger(BelfiusBusinessProcessBaseTestCase.class);

	private TaskService taskService = null;
	private RuntimeEngine runtimeEngine = null;
	private KieSession ksession = null;
	private String[] processDefinitionFileLocation = null;
	private Map<Long, WorkItem> activeWorkItemMap = null;

	/**
	 * Allows to load multiple process definition files (.bpmn)
	 * @param setupDataSource
	 * @param sessionPersistence
	 * @param processDefinitionFileLocation .bpmn files to be loaded. The root directory is src/main/resource.
	 */
	public BelfiusBusinessProcessBaseTestCase(boolean setupDataSource, boolean sessionPersistence, String... processDefinitionFileLocation) {
		super(setupDataSource, sessionPersistence);
		this.processDefinitionFileLocation = processDefinitionFileLocation;
	}

	public BelfiusBusinessProcessBaseTestCase(boolean setupDataSource, String persistenceUnitName, boolean sessionPersistence, String... processDefinitionFileLocation) {
		super(setupDataSource, sessionPersistence, persistenceUnitName);
		this.processDefinitionFileLocation = processDefinitionFileLocation;
	}


	//////////////////////////////////////
	// Runtime environment setup operations
	//////////////////////////////////////	

	@Before
	public void buildRuntimeEnvironment () {
		createRuntimeManager(Strategy.PROCESS_INSTANCE, "runtimeManagerInstance", processDefinitionFileLocation);
		runtimeEngine = getRuntimeEngine(ProcessInstanceIdContext.get());
		taskService = runtimeEngine.getTaskService();
		ksession = runtimeEngine.getKieSession();
		activeWorkItemMap = new HashMap<Long, WorkItem>();
	}

	/**
	 * Registers test WorkItemHandlers for each name provided.
	 * @param workItemNames
	 */
	protected void registerWorkItemHandlers(ProcessNode... workItems) {

		for (ProcessNode processNode : workItems) {
			// For the time being, only service tasks will be supported.
			// However, some other nodes should be foreseen in the near future (SUBPROCESS, EVENT_SUBPROCESS, BUSINESS_RULE).
			if(processNode.getNodeType().equals(NodeType.SERVICE_TASK))
				ksession.getWorkItemManager().registerWorkItemHandler(processNode.getNodeId(), getTestWorkItemHandler());
			else
				logger.warn(processNode.getNodeType() + " not supported, skipping it.");

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
	protected void signalEvent(String signalId, Object inputs, Long processInstanceId) {		
		ksession.signalEvent(signalId, inputs, processInstanceId);
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
	 * Asserts User Task Completed
	 * @param ksession Active KieSession
	 * @param workItem The active WorkItem whose name, status (active), inputs and outputs will be asserted.
	 * @param activityName The name of the activity that is expected to be asserted.
	 * @param inputs Input data to be asserted in the WorkItem.
	 * @param outputs Output data to be used when completing the WorkItem.
	 */
	public void assertUserTaskCompleted(Long processInstanceId, String userTaskName, String userId, Map<String, Object> inputs, Map<String, Object> outputs) {

		// We've found the user task in the user task list.
		List<TaskSummary> taskList = taskService.getTasksAssignedAsPotentialOwner(userId, "en-uk");
		TaskSummary targetUserTask = null;

		for (TaskSummary taskSummary : taskList) {
			if(userTaskName.equals(taskSummary.getName())) {
				targetUserTask = taskSummary;
				break;
			}
		}

		// We've found the user task in the user task list.
		assertNotNull(targetUserTask);

		// The task must be ready to be handled.
		assertNotEquals(targetUserTask, Status.Completed);
		assertNotEquals(targetUserTask, Status.Created);
		assertNotEquals(targetUserTask, Status.Error);
		assertNotEquals(targetUserTask, Status.Failed);
		assertNotEquals(targetUserTask, Status.Obsolete);
		assertNotEquals(targetUserTask, Status.Suspended);
		assertNotEquals(targetUserTask, Status.Exited);

		// Complete the task.
		taskService.start(targetUserTask.getId(), userId);
		taskService.complete(targetUserTask.getId(), userId, outputs);

		// Check that the task is completed.
		Task updatedTask = taskService.getTaskById(targetUserTask.getId());
		assertEquals(updatedTask.getTaskData().getStatus(), Status.Completed);
	}

}
