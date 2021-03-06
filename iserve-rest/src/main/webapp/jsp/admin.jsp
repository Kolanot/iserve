<%@ page import="com.google.inject.Injector" %>
<%@ page import="uk.ac.open.kmi.iserve.sal.manager.RegistryManager" %>

<%--
  ~ Copyright (c) 2013. Knowledge Media Institute - The Open University
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>

<%@ include file="include.jsp" %>
<%
    // http://turbomanage.wordpress.com/2009/12/11/how-to-inject-guice-objects-in-a-jsp/
    Injector inj = (Injector) pageContext.getServletContext().getAttribute(Injector.class.getName());
    RegistryManager registryManager = inj.getInstance(RegistryManager.class);
%>

<html>
<head>
    <link type="text/css" rel="stylesheet" href="<c:url value="
    /css/style.css"/>"/>
    <script src="http://code.jquery.com/jquery-latest.js"></script>
    <script>
        function deleteUrl(url) {
            $.ajax({
                url: url,
                type: 'DELETE',
                data: {submit: true}, // An object with the key 'submit' and value 'true;
                xhrFields: {  // send credentials
                    withCredentials: true
                },
                complete: function (e, xhr, settings) {
                    // Deleted
                    if (e.status === 200 || e.status == 204) {
                        alert("Data has been permanently deleted.");
                    } else if (e.status === 403) {
                        alert("Content was not deleted: you do not have the right permissions.");
                    } else if (e.status === 500) {
                        alert("Content was not deleted: there was an internal error.");
                    } else {
                        alert("Unable to delete the data. ");
                    }
                }
            })
        }

        function clearResources(registry) {
            switch (registry) {
                case "registry":
                    var r = confirm("Are you sure you want to clear the entire registry?");
                    if (r == true) {
                        deleteUrl("registry");
                    }
                    break;

                case "id/services":
                    var r = confirm("Are you sure you want to clear the services?");
                    if (r == true) {
                        deleteUrl("services");
                    }
                    break;

                case "id/documents":
                    var r = confirm("Are you sure you want to clear the documents?");
                    if (r == true) {
                        deleteUrl("documents");
                    }
                    break;
            }
        }

    </script>
</head>
<body>

<h1>iServe Administration</h1>

<p>You are currently logged as root from <%= request.getRemoteHost() %>
</p>

<p>See <a href="<c:url value="/jsp/configuration.jsp"/>">Server's configuration.</a></p>

<h2>Server Statistics</h2>

<p>Services registered: <%= registryManager.getServiceManager().listServices().size() %>
</p>

<p>Documents registered: <%= registryManager.getDocumentManager().listDocuments().size() %>
</p>

<p>See <a href="<c:url value="/control/show-stats"/>">Elda's Statistics</a>.</p>

<h2>Management Functions</h2>

<p>
    <button onclick="clearResources('registry')">Clear Registry</button>
    This operation will clear entirely the registry. All content will be deleted. (Warning: This
    operation cannot be
    reverted!)
</p>
<p>
    <button onclick="clearResources('id/services')">Clear Services</button>
    This operation will clear the services in the registry. All the services registered will be
    deleted. (Warning: This
    operation cannot be reverted!)
</p>
<p>
    <button onclick="clearResources('id/documents')">Clear Documents</button>
    This operation will clear the documents in the registry. All the documents registered will be
    deleted. (Warning:
    This operation cannot be reverted!)
</p>

<p>
    <button onClick="parent.location='/control/clear-cache'">Clear Elda's Cache</button>
</p>


<p><a href="<c:url value="/jsp/index.jsp"/>">Return to the home page.</a></p>

<p><a href="<c:url value="/jsp/logout.jsp"/>">Log out.</a></p>

</body>
</html>