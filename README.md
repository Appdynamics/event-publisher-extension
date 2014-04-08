# Event Consumption Extension  

##Use Case

The Event Consumption Extension executes various scripts at user configurable intervals and based on exit code sends events to Controller.

This extension works only with the standalone machine agent.

##Installation
1. Run 'mvn clean install' from the event-consumption-extension directory
2. Download the file EventConsumptionMonitor.zip found in the 'target' directory into \<machineagent install dir\>/monitors/
3. Unzip the downloaded file and cd into EventConsumptionMonitor
4. Open the monitor.xml file and edit the project path to the EventConsumptionMonitor directory that was just created.
5. Open the events.xml file and add the appropriate scripts such as those in [the events.xml example](https://github.com/Appdynamics/event-consumption-extension/blob/master/README.md#eventsxml).
6. Restart the Machine Agent.
7. In the AppDynamics controller, look for events in \<App ID\> -> Events

**Note**: The event scripts must have read access by the Machine Agent for the monitoring extension to execute them.

##Directory Structure

<table><tbody>
<tr>
<th align="left"> Directory/File </th>
<th align="left"> Description </th>
</tr>
<tr>
<td class='confluenceTd'> src/main/java </td>
<td class='confluenceTd'> Contains source code to Event Consumption Extension  </td>
</tr>
<tr>
<td class='confluenceTd'> src/main/resources </td>
<td class='confluenceTd'> Contains monitor.xml and events.xml </td>
</tr>
<tr>
<td class='confluenceTd'> target </td>
<td class='confluenceTd'> Only obtained when using maven. Run 'maven clean install' the distributable .zip file </td>
</tr>
<tr>
<td class='confluenceTd'> pom.xml </td>
<td class='confluenceTd'> Maven build script to package the project (required only if changing Java code) </td>
</tr>
</tbody>
</table>

##XML Examples

###  monitor.xml


| Param | Description |
| ----- | ----- |
| project\_path | Location of the events script root directory |
| worker\_count | Number of worker thread(s) that will be spawned as part of the Executor Pool to execute the scripts |

~~~~
<monitor>
        <name>EventConsumptionMonitor</name>
        <type>managed</type>
        <description>Event Consumption Monitor</description>
        <monitor-configuration></monitor-configuration>
        <monitor-run-task>
                <execution-style>continuous</execution-style>
                <name>Event Consumption Monitor Run Task</name>
                <display-name>Event Consumption Monitor Task</display-name>
                <description>Event Consumption Monitor Task</description>
                <type>java</type>
                <execution-timeout-in-secs>60</execution-timeout-in-secs>
                <task-arguments>
                    <argument name="project_path" is-required="true" default-value="/home/satish/AppDynamics/MachineAgent/monitors/EventConsumptionMonitor"/>
                    <!-- Controls how many worker threads would be spawned to execute the scripts -->
                    <argument name="worker_count" is-required="false" default-value="5"/>
		       </task-arguments>
                <java-task>
                    <classpath>event-consumption-extension.jar;lib/dom4j-1.6.1.jar;lib/xml-apis-1.0.b2.jar</classpath>
                        <impl-class>com.appdynamics.monitors.events.EventConsumptionMonitor</impl-class>
                </java-task>
        </monitor-run-task>
</monitor>
~~~~

###events.xml

| Param | Description |
| ---- | ---- |
| \<name\> | Name of the event |
| \<path\>  | Path to the shell file |
| \<period\>  | (seconds) - Delay between consecutive  calls. |
| \<max-wait-time\>  | (seconds) - Wait time before force killing this script. If not specified, assumes 5 seconds. |
| \<arguments\> | Arguments that will be passed to the script |


~~~~
    <events>
        <event>
            <name>Tomcat Status</name>
            <summary>Tomcat Status</summary>
            <comment>Event for tomcat status</comment>
            <path>/home/satish/AppDynamics/Code/extensions/event-consumption-extension/src/main/resources/config/scripts/script.sh</path>
            <outputs>
                <output>
                    <exitCode>0</exitCode>
                    <status>Down</status>
                </output>
                <output>
                    <exitCode>1</exitCode>
                    <status>Up</status>
                </output>
            </outputs>
            <arguments/>
            <!--Number of seconds to wait to execute the script periodically -->
            <period>5</period>
            <!-- Number of seconds to wait before force killing this script. If not specified, assumes 5 seconds-->
            <max-wait-time>5</max-wait-time>
        </event>
    </events>
~~~~

##Contributing

Always feel free to fork and contribute any changes directly via [GitHub](https://github.com/Appdynamics/event-consumption-extension).

##Community

Find out more in the [AppSphere]() community.

##Support

For any questions or feature request, please contact [AppDynamics Center of Excellence](mailto:ace-request@appdynamics.com).

