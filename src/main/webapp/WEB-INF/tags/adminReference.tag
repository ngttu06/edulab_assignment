<%@ tag pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ attribute name="name" required="true" %>
<%@ attribute name="label" required="true" %>
<%@ attribute name="resource" required="true" %>
<%@ attribute name="multiple" type="java.lang.Boolean" %>
<%@ attribute name="required" type="java.lang.Boolean" %>
<%@ attribute name="selected" type="java.util.List" %>
<div class="admin-reference" data-reference="${resource}" data-multiple="${multiple == true}">
    <label class="form-label" for="ref-${name}"><c:out value="${label}"/>${required == true ? ' *' : ''}</label>
    <select id="ref-${name}" name="${name}" class="form-select" ${multiple == true ? 'multiple size="5"' : ''} ${required == true ? 'required' : ''} aria-describedby="help-${name}">
        <c:if test="${multiple != true}"><option value="">— Chọn —</option></c:if>
        <c:forEach var="option" items="${selected}"><option value="${option.id}" selected><c:out value="${option.label}"/></option></c:forEach>
    </select>
    <div class="admin-reference-search"><label class="visually-hidden" for="find-${name}">Tìm ${label}</label><input id="find-${name}" type="search" class="form-control" maxlength="160" placeholder="Nhập tên để tìm…" autocomplete="off"><button class="btn btn-outline-secondary" type="button" data-find-reference>Tìm</button></div>
    <small id="help-${name}" class="admin-field-help">Tối đa 50 kết quả mỗi lần tìm.${multiple == true ? ' Giữ Ctrl / Command để chọn nhiều mục.' : ''}</small>
    <span class="admin-reference-status" role="status"></span>
</div>