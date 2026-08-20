import Foundation

struct Material {
    let id: String
    let title: String
    let description: String
    let fileType: String
    let fileSize: Int64
    let fileUrl: String
    let fileName: String
    let subjectId: String
    let subjectName: String
    let moduleId: String
    let moduleName: String
    let visibility: String
    let section: String?
    let createdAt: Date
    
    var isDirectSubjectFile: Bool {
        return moduleId.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}
