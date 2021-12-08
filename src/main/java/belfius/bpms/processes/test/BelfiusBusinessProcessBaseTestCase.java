package belfius.bpms.processes.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.core.command.runtime.process.SetProcessInstanceVariablesCommand;
import org.jbpm.persistence.correlation.CorrelationKeyInfo;
import org.jbpm.test.JbpmJUnitBaseTestCase;
import org.junit.After;
import org.junit.Before;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.task.TaskService;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.Task;
import org.kie.api.task.model.TaskSummary;
import org.kie.internal.runtime.manager.context.ProcessInstanceIdContext;

public class BelfiusBusinessProcessBaseTestCase extends JbpmJUnitBaseTestCase {
	
	private TaskService taskService = null;
	private RuntimeEngine runtimeEngine = null;
	private KieSession ksession = null;
	private String[] processDefinitionFileLocation = null;
	
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
	
	@Before
	public void buildRuntimeEnvironment () {
		createRuntimeManager(Strategy.PROCESS_INSTANCE, "runtimeManagerInstance", processDefinitionFileLocation);
		runtimeEngine = getRuntimeEngine(ProcessInstanceIdContext.get());
		taskService = runtimeEngine.getTaskService();
		ksession = runtimeEngine.getKieSession();
	}
	
	/**
	 * Starts a new process instance with inputs.
	 * @param processId Process Definition ID
	 * @param processInputs Process input variables
	 * @return
	 */
	protected void registerWorkItemHandler(String workItemName, WorkItemHandler workItemHandler) {
		ksession.getWorkItemManager().registerWorkItemHandler(workItemName, workItemHandler);
	}
	
	/**
	 * Starts a new process instance with inputs.
	 * @param processId Process Definition ID
	 * @param processInputs Process input variables
	 * @return
	 */
	protected long startProcessInstance(String processId, Map<String, Object> processInputs) {
		
		ProcessInstance processInstance = ksession.createProcessInstance(processId, processInputs);
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
	public void assertServiceTaskCompleted(Long processInstanceId, 
			WorkItem workItem, String workItemName, String workItemId, Map<String, Object> inputs, Map<String, Object> outputs) {
		
		// Validating that the workitem has been activated.
		assertNotNull(workItem);
		
		// Validating that the workitem we're validating is the expected one.
		assertEquals(workItem.getName(), workItemId);
		
		// Validating that the workitem has been triggered.
		assertNodeTriggered(processInstanceId, workItemName);
		
		// Validating inputs.
		if(inputs != null) {
			for (Map.Entry<String, Object> inputVariable : inputs.entrySet()) {
				assertEquals(inputVariable.getValue(), workItem.getParameter(inputVariable.getKey()));
				
		    }
		}

		// Completing the workitem.
		ksession.getWorkItemManager().completeWorkItem(workItem.getId(), outputs);
	}
	
	
	//////////////////////////////////////
	// Service Task Assertions
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
