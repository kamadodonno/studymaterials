import Foundation

struct Constants {
    static let appName = "Study Materials"
    
    // Firestore Collections
    static let collUsers = "users"
    static let collEnrollments = "enrollmentNumbers"
    static let collAdmins = "admins"
    static let collDepartments = "departments"
    static let collAcademicYears = "academicYears"
    static let collSections = "sections"
    static let collSubjects = "subjects"
    static let collModules = "modules"
    static let collMaterials = "materials"
    static let collSubmissions = "submissions"
    static let collAnnouncements = "announcements"
    
    // Exact Sections (1-11, 13-18, Other)
    static let defaultSectionNames = [
        "Section 1", "Section 2", "Section 3", "Section 4",
        "Section 5", "Section 6", "Section 7", "Section 8",
        "Section 9", "Section 10", "Section 11", "Section 13",
        "Section 14", "Section 15", "Section 16", "Section 17",
        "Section 18", "Other"
    ]
}
