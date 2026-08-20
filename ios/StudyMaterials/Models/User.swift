import Foundation

struct User {
    let uid: String
    let name: String
    let enrollmentNumber: String
    let section: String
    let otherSection: String?
    
    var displaySection: String {
        if section.lowercased() == "other", let other = otherSection, !other.isEmpty {
            return "Other (\(other))"
        }
        return section.isEmpty ? "Section" : section
    }
}
