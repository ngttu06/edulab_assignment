package vn.edu.khtn8.school;

/** SQL fragments use only internal aliases and numeric user IDs, never request strings. */
public final class ContentAccess {
    private ContentAccess() { }
    public static String lesson(String alias, long userId) { return visible(alias, "class_lessons", "lesson_id", userId); }
    public static String quiz(String alias, long userId) { return visible(alias, "quiz_classes", "quiz_id", userId); }
    private static String visible(String alias, String table, String column, long userId) {
        return " (" + alias + ".deleted_at IS NULL AND (" + alias + ".class_only=FALSE OR " + alias + ".created_by=" + userId
                + " OR EXISTS(SELECT 1 FROM users access_user WHERE access_user.id=" + userId + " AND access_user.role='ADMIN')"
                + " OR EXISTS(SELECT 1 FROM " + table + " access_class WHERE access_class." + column + "=" + alias + ".id AND ("
                + "EXISTS(SELECT 1 FROM class_enrollments ae JOIN classrooms ac ON ac.id=ae.classroom_id AND ac.archived_at IS NULL WHERE ae.classroom_id=access_class.classroom_id AND ae.student_id=" + userId + " AND ae.active=TRUE)"
                + " OR EXISTS(SELECT 1 FROM class_teachers atc WHERE atc.classroom_id=access_class.classroom_id AND atc.teacher_id=" + userId + "))))) ";
    }
}