# Event Publisher Extension  

##Use Case

The Event Publisher Extension executes various scripts at user configurable intervals and based on exit code sends events to Controller.

This extension works only with the standalone machine agent.

##Installation
1. Run 'mvn clean install' from the event-publisher-extension directory
2. Download the file EventPublisherMonitor.zip found in the 'target' directory into \<machineagent install dir\>/monitors/
3. Unzip the downloaded file and cd into EventPublisherMonitor
4. Open the monitor.xml file and edit the project path to the EventPublisherMonitor directory that was just created.
5. Open the events.xml file and add the appropriate scripts such as those in [the events.xml example](https://github.com/Appdynamics/event-publisher-extension/blob/master/README.md#eventsxml).
6. Restart the Machine Agent by setting -Dmetric.http.listener=true.
7. In the AppDynamics controller, look for events in \<App ID\> -> Events

**Note**: The event scripts must have read access by the Machine Agent for the monitoring extension to execute them. <br>
**Note**: While starting the machine agent please set the property metric.http.listener to true <br>
 ex: java -Dmetric.http.listener=true -jar machineagent.jar

##Directory Structure

<table><tbody>
<tr>
<th align="left"> Directory/File </th>
<th align="left"> Description </th>
</tr>
<tr>
<td class='confluenceTd'> src/main/java </td>
<td class='confluenceTd'> Contains source code to Event Publisher Extension  </td>
</tr>
<tr>
<td class='confluenceTd'> src/main/resources </td>
<td class='confluenceTd'> Contains monitor.xml and events.xml </td>
</tr>
<tr>
<td class='confluenceTd'> target </td>
<td class='confluenceTd'> Only obtained when using maven. Run 'maven clean install' to get the distributable .zip file </td>
</tr>
<tr>
<td class='confluenceTd'> pom.xml </td>
<td class='confluenceTd'> Maven script file (required only if changing Java code) </td>
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
    <name>EventPublisherMonitor</name>
    <type>managed</type>
    <description>Event Publisher Monitor</description>
    <monitor-configuration></monitor-configuration>
    <monitor-run-task>
        <execution-style>periodic</execution-style>
        <name>Event Publisher Monitor Run Task</name>
        <display-name>Event Publisher Monitor Task</display-name>
        <description>Event Publisher Monitor Task</description>
        <type>java</type>
        <execution-timeout-in-secs>60</execution-timeout-in-secs>
        <task-arguments>
            <argument name="project_path" is-required="true" default-value="/home/satish/AppDynamics/MachineAgent/monitors/EventPublisherMonitor"/>
            <!-- Controls how many worker threads would be spawned to execute the scripts -->
            <argument name="worker_count" is-required="false" default-value="5"/>
        </task-arguments>
        <java-task>
            <classpath>event-publisher-extension.jar;lib/xstream-1.4.7.jar;lib/xmlpull-1.1.3.1.jar;lib/xpp3_min-1.1.4c.jar</classpath>
            <impl-class>com.appdynamics.monitors.events.EventPublisherMonitor</impl-class>
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
| \<max-wait-time\>  | (seconds) - seconds to wait before force killing this script. If not specified or is more than period then assumes the value specified in period. |
| \<arguments\> | Arguments that will be passed to the script |


~~~~
    <events>
        <event>
            <name>Tomcat Status</name>
            <summary>Tomcat Status</summary>
            <comment>Event for tomcat status</comment>
            <path>/home/satish/AppDynamics/Code/extensions/event-publisher-extension/src/main/resources/config/scripts/script.sh</path>
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
            <!-- Number of seconds to wait before force killing this script. If not specified or is more than period then assumes the value specified in period-->
            <max-wait-time>5</max-wait-time>
        </event>
    </events>
~~~~

###Example script

~~~~
#!/bin/bash
COUNT=`ps -ef | grep tomcat| grep -v grep | wc -l`
if [ "$COUNT" = 0 ]; then
    exit 0
else
    exit 1
fi
~~~~

Above script file will check whether tomcat is up or not. Exit code from the script will be looked up in the events.xml to get the status.
From the above script 
* if exit code is 0 then the status is Down
* if exit code is 1 then the status is Up


##Contributing

Always feel free to fork and contribute any changes directly via [GitHub](https://github.com/Appdynamics/event-publisher-extension).

##Community

Find out more in the [AppSphere]() community.

##Support

For any questions or feature request, please contact [AppDynamics Center of Excellence](mailto:ace-request@appdynamics.com).

