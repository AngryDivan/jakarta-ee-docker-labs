<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<body>
  <h2>Hello from Servlet + JSP</h2>
  <p>DB time (JDBC): ${dbNow}</p>
  <p><a href="<%=request.getContextPath()%>/tasks.xhtml">Go to JSF tasks</a></p>
  <p><a href="<%=request.getContextPath()%>/api/tasks">Open REST tasks JSON</a></p>
</body>
</html>