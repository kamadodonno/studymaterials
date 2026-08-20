import Foundation

#if canImport(FirebaseFirestore)
import FirebaseFirestore
#endif

class FirebaseService {
    static let shared = FirebaseService()
    
    private init() {}
    
    func fetchSubjects(completion: @escaping ([Subject]) -> Void) {
        #if canImport(FirebaseFirestore)
        let db = Firestore.firestore()
        db.collection(Constants.collSubjects).getDocuments { (snapshot, error) in
            guard let docs = snapshot?.documents, error == nil else {
                completion(self.getMockSubjects())
                return
            }
            let subjects = docs.map { doc -> Subject in
                let data = doc.data()
                return Subject(
                    id: doc.documentID,
                    name: data["name"] as? String ?? "Subject",
                    code: data["code"] as? String ?? "",
                    departmentId: data["departmentId"] as? String ?? "",
                    academicYearId: data["academicYearId"] as? String ?? "",
                    moduleCount: data["moduleCount"] as? Int ?? 0,
                    materialCount: data["materialCount"] as? Int ?? 0,
                    order: data["order"] as? Int ?? 0
                )
            }
            completion(subjects)
        }
        #else
        completion(getMockSubjects())
        #endif
    }
    
    func fetchAnnouncements(section: String, completion: @escaping ([Announcement]) -> Void) {
        #if canImport(FirebaseFirestore)
        let db = Firestore.firestore()
        db.collection(Constants.collAnnouncements).getDocuments { (snapshot, error) in
            guard let docs = snapshot?.documents, error == nil else {
                completion([])
                return
            }
            let announcements = docs.compactMap { doc -> Announcement? in
                let data = doc.data()
                let targetType = data["targetType"] as? String ?? "all"
                let targetSection = data["targetSection"] as? String
                
                if targetType == "all" || targetSection == section {
                    let ts = (data["createdAt"] as? Timestamp)?.dateValue() ?? Date()
                    return Announcement(
                        id: doc.documentID,
                        title: data["title"] as? String ?? "Announcement",
                        message: data["message"] as? String ?? "",
                        targetType: targetType,
                        targetSection: targetSection,
                        authorName: data["authorName"] as? String ?? "Faculty",
                        createdAt: ts
                    )
                }
                return nil
            }
            completion(announcements)
        }
        #else
        completion([])
        #endif
    }
    
    private func getMockSubjects() -> [Subject] {
        return [
            Subject(id: "sub_1", name: "Operating Systems", code: "CS301", departmentId: "d1", academicYearId: "y3", moduleCount: 5, materialCount: 12, order: 1),
            Subject(id: "sub_2", name: "Database Management", code: "CS302", departmentId: "d1", academicYearId: "y3", moduleCount: 4, materialCount: 8, order: 2),
            Subject(id: "sub_3", name: "Computer Networks", code: "CS303", departmentId: "d1", academicYearId: "y3", moduleCount: 5, materialCount: 15, order: 3),
            Subject(id: "sub_4", name: "Software Engineering", code: "CS304", departmentId: "d1", academicYearId: "y3", moduleCount: 4, materialCount: 6, order: 4)
        ]
    }
}
