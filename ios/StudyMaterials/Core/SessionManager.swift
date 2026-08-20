import Foundation

class SessionManager {
    static let shared = SessionManager()
    
    private let keyIsLoggedIn = "is_logged_in"
    private let keyUid = "user_uid"
    private let keyName = "user_name"
    private let keyEnrollment = "user_enrollment"
    private let keySection = "user_section"
    private let keyOtherSection = "user_other_section"
    private let keySubjectOrder = "custom_subject_order"
    
    private init() {}
    
    var isLoggedIn: Bool {
        return UserDefaults.standard.bool(forKey: keyIsLoggedIn)
    }
    
    var currentUser: User? {
        guard isLoggedIn else { return nil }
        return User(
            uid: UserDefaults.standard.string(forKey: keyUid) ?? "",
            name: UserDefaults.standard.string(forKey: keyName) ?? "",
            enrollmentNumber: UserDefaults.standard.string(forKey: keyEnrollment) ?? "",
            section: UserDefaults.standard.string(forKey: keySection) ?? "",
            otherSection: UserDefaults.standard.string(forKey: keyOtherSection)
        )
    }
    
    func saveUser(name: String, enrollment: String, section: String, otherSection: String?) {
        UserDefaults.standard.set(true, forKey: keyIsLoggedIn)
        UserDefaults.standard.set(UUID().uuidString, forKey: keyUid)
        UserDefaults.standard.set(name, forKey: keyName)
        UserDefaults.standard.set(enrollment, forKey: keyEnrollment)
        UserDefaults.standard.set(section, forKey: keySection)
        if let other = otherSection, !other.isEmpty {
            UserDefaults.standard.set(other, forKey: keyOtherSection)
        } else {
            UserDefaults.standard.removeObject(forKey: keyOtherSection)
        }
    }
    
    func getCustomSubjectOrder() -> [String] {
        let raw = UserDefaults.standard.string(forKey: keySubjectOrder) ?? ""
        if raw.isEmpty { return [] }
        return raw.components(separatedBy: ",")
    }
    
    func saveCustomSubjectOrder(_ order: [String]) {
        UserDefaults.standard.set(order.joined(separator: ","), forKey: keySubjectOrder)
    }
    
    func clearSession() {
        UserDefaults.standard.removeObject(forKey: keyIsLoggedIn)
        UserDefaults.standard.removeObject(forKey: keyUid)
        UserDefaults.standard.removeObject(forKey: keyName)
        UserDefaults.standard.removeObject(forKey: keyEnrollment)
        UserDefaults.standard.removeObject(forKey: keySection)
        UserDefaults.standard.removeObject(forKey: keyOtherSection)
    }
}
