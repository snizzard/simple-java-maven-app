package com.mycompany.app;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.json.JSONArray;
import org.json.JSONObject;


public class ZAPIConnect
{
    /* these are values I use for a test project
    I created for the purpose of writing and testing code */
    private static String baseUrl = "http://localhost:7990/rest";
    private static String usernamePass = "sqa:sqa";

    /*
     * baseUrl is a static string because any request
     * sent to Zapi's REST API has a URL that starts with this base.
     * usernamePass is used in the getEncoding() method used to
     * encode user credentials into base64 format so it can be
     * used in authorization headers.
     */
    //--->private static String baseUrl = "https://jira.wacom.com/rest";
    //--->private static String usernamePass = "cash.best:sk8ordie";

    private static String projectsUrl = baseUrl + "/api/2/project";
    private static String zapiLatestUrl = baseUrl + "/zapi/latest";
    /*
     * versionId has a default value of "-1"
     * which corresponds to the "Unscheduled" test cycle
     */
    public String versionId = "-1";
    private String currentCycle;
    private String currentFolder = "0";
    public String projectId;

    public testExecution currentExecution;

    // below is an arrayList in the event that there are several version ids to sift through
    //private ArrayList<String> versionIds = new ArrayList<String>();
    private ArrayList<String> cycleIds = new ArrayList<>();
    private ArrayList<String> testCaseIds = new ArrayList<>();
    public List<Integer> exIds;
    private ArrayList<String> cycleFolders = new ArrayList<>();
    public ArrayList<testExecution> execObjects = new ArrayList<>();
    JSONObject currentCycleData;


    /* JSON object for storing test/cycle info like IDs and names
     * so information can be quickly accessed without the need for
     * a new zapi REST request every time data is needed*/
    JSONObject projectDetails;

    public ZAPIConnect(String projectName)
    {
        setProjectId(projectName);
        // verionId will remain its default value of -1 because
        // for now the only versionId needed for FY37 project is
        // the "Unscheduled" version
        //setVersionId();
        exIds = getExecutionIds();
//        for(int id : exIds)
//        {
//            createExecutionObject(id);
//        }
    }

    public void newExecution(int exId)
    {
        this.currentExecution = new testExecution(getStepResultIds(exId), exId);
    }

    /*
     * getEncoding() method
     * used for encoding user credentials for authentication of REST calls
     * returns a base64 encoded string
     */
    private String getEncoding()
    {
        String encoding = null;
        try
        {
            encoding = Base64.getEncoder().encodeToString((usernamePass)
                    .getBytes(StandardCharsets.UTF_8));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return encoding;
    }

    /*
     * requestZapiData() method
     * used for GET requests from zapi server
     * returns a JSON array, which usually contains
     * a number of JSON objects representing testCycleObjects,
     * testStepObjects, etc.
     */
    public JSONArray requestZapiData(String url)
    {
        URL zUrl = null;
        String result = null;
        JSONObject output = null;
        JSONArray json = null;
        try {
            zUrl = new URL(url);

            String encoding = getEncoding();

            HttpURLConnection connection = (HttpURLConnection) zUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setDoOutput(true);
            // setting this property with these parameters is what authenticates
            // the automation for read/write permissions to Zapi server
            connection.setRequestProperty("Authorization", "Basic " + encoding);
            InputStream content = (InputStream)connection.getInputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(content));
            // scanner takes "content" as input and writes to "result" string line-by-line
            Scanner s = new Scanner(content).useDelimiter("\\A");
            result = s.hasNext() ? s.next() : "";

        } catch (Exception e) {
            e.printStackTrace();
        }
        /*
         * Zapi always returns response data in JSON form. Sometimes its this format:
         * [{"someId":"someVal"},{"otherId":"otherVal"}]
         * where its a JSON array of JSON objects
         */
        try
        {
            json = new JSONArray(result);
        }
        catch (org.json.JSONException e)
        {
            /*
             * ....other times it returns a simple JSON object with format
             * {"someId":{"someKey":"someVal"}}
             * with no surrounding array brackets. the method should
             * always return a "JSONArray", so I just surround the string
             * in brackets before converting so it can be made into a JSONArray
             */
            result = String.format("[%s]", result);
            json = new JSONArray(result);
        }

        return json;
    }

    /*
     * putZapiData() method
     * used for updating stepResults, testCaseResults, etc.
     * although we might want it to return "connection.getResponseCode()"
     */
    private void putZapiData(String url, String putData)
    {
        URL pUrl = null;
        try
        {
            pUrl = new URL(url);
            String encoding = getEncoding();

            HttpURLConnection connection = (HttpURLConnection) pUrl.openConnection();
            connection.setRequestMethod("PUT");
            connection.setDoOutput(true);
            // setting headers for request
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Basic " + encoding);
            connection.setRequestProperty("Accept", "application/json");

            // output writer for writing putData to the "PUT" request for updating
            OutputStreamWriter osw = new OutputStreamWriter(connection.getOutputStream());
            osw.write(putData);
            // flush/close writer, which confirms changes so they update in Zephyr
            osw.flush();
            osw.close();
            // should maybe be the return value for this method
            // so automation can confirm PUT was a success
            if(connection.getResponseCode() >= 200)
            {
                System.out.println("putZapiData returned status OK");
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public String getProjectId()
    {
        return this.projectId;
    }

    /*
     * setProjectId() method
     * projectId is a necessary parameter for several important
     * Zapi queries.
     */
    public void setProjectId(String projectName)
    {
        String pName = null;
        projectName = projectName.toUpperCase();
        JSONArray result = requestZapiData(projectsUrl);
        System.out.println("Finding project...");
        for (int i=0; i<result.length(); i++)
        {
            JSONObject item = result.getJSONObject(i);
            //System.out.println("item: " + item);
            pName = item.getString("name").toUpperCase();

            //System.out.println("other project: " + pName);
            if(pName.equals(projectName))
            {
                System.out.println(pName);
                System.out.println("Project id: " + item.get("id"));
                this.projectId = item.getString("id");
            }
        }
    }

    public String getVersionId()
    {
        return this.versionId;
    }

    /*
     * setVersionId() method
     * versionId is also a necessary parameter for several important
     * Zapi queries. Default value is "-1" which corresponds to the
     * "Unscheduled" test cycle, the most commonly used cycle
     */
    public void setVersionId()
    {
        String url = projectsUrl + "/" + this.projectId + "/versions";
        JSONArray result = requestZapiData(url);
        //System.out.println(result);
    }

    /*
     * setCycleIds() method
     * cycleId is a necessary parameter for getting both execution ids
     * and folder ids for a given test cycle
     */
    public void getCycleIds()
    {
        String url = zapiLatestUrl + "/cycle?projectId=" + this.projectId;
        JSONArray result = requestZapiData(url);

        JSONObject r = result.getJSONObject(0);
        JSONArray j = r.getJSONArray("-1");

        JSONObject cycleJson = j.getJSONObject(0);
        Iterator<String> keys = cycleJson.keys();
        while(keys.hasNext())
        {
            String key = keys.next();
            if(key.equals("recordsCount"))
            {
                continue;
            }
            else
            {
                JSONObject cj = cycleJson.getJSONObject(key);
                try
                {
                    if (cj.getInt("totalFolders") > 0)
                    {
                        this.cycleFolders.add(key);
                    }
                }
                catch (org.json.JSONException e)
                {
                    this.cycleIds.add(key);
                }
            }
        }
        // System.out.println("cycleIds: " + cycleIds.size());
        //System.out.println("cycle folders: " + cycleFolders.size());
        //String joined = String.join(", ", cycleIds);
        //System.out.println(joined);
    }

    /*
     * setFolderIds() method
     * folderId is a necessary parameter for gathering execution ids
     * for test cases contained in a test case folder
     */
    public List<Integer> getFolderIds(String cycle)
    {
        ArrayList<Integer> folderIds = new ArrayList<>();
        String url = zapiLatestUrl + "/cycle/" + cycle + String.format("/folders?projectId=%s&versionId=%s", this.projectId, this.versionId);
        JSONArray result = requestZapiData(url);
        for(int i=0; i<result.length(); i++)
        {
            JSONObject folderJson = result.getJSONObject(i);
            folderIds.add(folderJson.getInt("folderId"));
        }
        return folderIds;
    }

    /*
     * setExecutionIds() method
     * ExecutionId is a necessary parameter for getting and
     * updating step results for a given test case
     */
    public List<Integer> getExecutionIds()
    {
        getCycleIds();
        ArrayList<Integer> executionIds = new ArrayList<>();
        String url;

        // "action=expand" is used to get more details about an execution object
        // without this value, Zapi returns a "short-hand" response that does not
        // include the id # of each execution that is needed in other methods
        for (String cycleFolder : cycleFolders) {
            List<Integer> folders = getFolderIds(cycleFolder);
            for (int folder : folders) {
                url = zapiLatestUrl + String.format("/execution/?action=expand&cycleId=%s&folderId=%s", cycleFolder, folder);
                JSONArray result = requestZapiData(url);
                // result will sometimes return a JSONArray containing a single object
                JSONObject e = result.getJSONObject(0);
                JSONArray execJsonArray = e.getJSONArray("executions");
                for (int c = 0; c < execJsonArray.length(); c++) {
                    JSONObject j = execJsonArray.getJSONObject(c);
                    executionIds.add(j.getInt("id"));
                }
            }

        }
        for (String cycleId : cycleIds) {
            url = zapiLatestUrl + String.format("/execution/?action=expand&cycleId=%s", cycleId);
            if(cycleId.equals("-1"))
            {
                url = url + String.format("&projectId=%s", projectId);
            }
            JSONArray result2 = requestZapiData(url);
            // result will sometimes return a JSONArray containing a single object
            JSONObject e = result2.getJSONObject(0);
            JSONArray execJsonArray = e.getJSONArray("executions");
            for (int n = 0; n < execJsonArray.length(); n++) {
                JSONObject j = execJsonArray.getJSONObject(n);
                executionIds.add(j.getInt("id"));
            }
        }
        return executionIds;
    }

    /*
     * setStepResultIds() method
     * StepResultId is a necessary parameter for getting information about
     * and updating the status of step results for a given test case
     */
    public JSONArray getStepResultIds(int execId)
    {
        String url = zapiLatestUrl + String.format("/stepResult?executionId=%s", execId);
        //System.out.println(url);
        JSONArray result = requestZapiData(url);

        return result;
    }

    public void createExecutionObject(int exId)
    {
        JSONArray steps = getStepResultIds(exId);
        testExecution exObj = new testExecution(steps, exId);
        execObjects.add(exObj);
    }
    /*
     * updateStepResult() method
     *  method for updating the status of a test step.
     *  status should be a string representation of one of these values:
     * -1: step is unexecuted
     *  1: step was executed with PASS result
     *  2: step was executed with FAIL result
     *  3: step is a Work-In-Progress
     *  4: step is BLOCKED for some reason
     */
    public void updateStepResult(int stepResId, String status)
    {
        /*
         *  status should be a string representation of one of these values:
         * -1: step is unexecuted
         *  1: step was executed with PASS result
         *  2: step was executed with FAIL result
         *  3: step is a Work-In-Progress
         *  4: step is BLOCKED for some reason
         */
        String url = zapiLatestUrl + String.format("/stepResult/%d", stepResId);
        String update = String.format("{\"status\":\"%s\"}", status);
        putZapiData(url, update);
    }

    /////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////

    public class testExecution
    {
        public int executionId;
        public int versionId;
        public String versionName;
        public int projectId;
        public String cycleName;
        public int cycleId;
        JSONObject executionData;
        public String key;
        public JSONArray testStepArray;
        public testExecution(JSONArray stepData, int id)
        {
            testStepArray = stepData;
            executionId = id;

            String url = ZAPIConnect.zapiLatestUrl + "/execution/" + id;
            JSONArray result = ZAPIConnect.this.requestZapiData(url);
            JSONObject json = result.getJSONObject(0);
            executionData = json.getJSONObject("execution");
            key = executionData.getString("issueKey");
        }

        public int getStepId(int orderId)
        {
            int thisStepId = 0;
            int ordId = 0;
            int i = 0;
            while (ordId != orderId)
            {
                JSONObject json = testStepArray.getJSONObject(i);
                ordId = json.getInt("orderId");
                thisStepId = json.getInt("id");
                i++;
            }
            return thisStepId;
        }
    }

}



