import Foundation

struct Announcement {
    let id: String
    let title: String
    let message: String
    let targetType: String // "all" or "section"
    let targetSection: String?
    let authorName: String
    let createdAt: Date
}
