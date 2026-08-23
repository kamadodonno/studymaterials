import Foundation

class FirebaseService {
    static let shared = FirebaseService()
    
    private let projectId = "pu-materials"
    private let baseUrl = "https://firestore.googleapis.com/v1/projects/pu-materials/databases/(default)/documents"
    private let storageBucket = "pu-materials.firebasestorage.app"
    
    private init() {}
    
    // MARK: - Fetch Live Subjects from Firestore
    func fetchSubjects(completion: @escaping ([Subject]) -> Void) {
        guard let url = URL(string: "\(baseUrl)/subjects?pageSize=100") else {
            completion([])
            return
        }
        
        URLSession.shared.dataTask(with: url) { data, response, error in
            guard let data = data, error == nil else {
                DispatchQueue.main.async { completion([]) }
                return
            }
            
            do {
                guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let documents = json["documents"] as? [[String: Any]] else {
                    DispatchQueue.main.async { completion([]) }
                    return
                }
                
                let subjects: [Subject] = documents.compactMap { doc in
                    guard let fields = doc["fields"] as? [String: Any] else { return nil }
                    let docPath = doc["name"] as? String ?? ""
                    let docId = docPath.components(separatedBy: "/").last ?? UUID().uuidString
                    
                    let isActive = self.parseBool(fields["isActive"]) ?? true
                    if !isActive { return nil }
                    
                    let name = self.parseString(fields["name"]) ?? "Subject"
                    let code = self.parseString(fields["code"]) ?? ""
                    let deptId = self.parseString(fields["departmentId"]) ?? ""
                    let yearId = self.parseString(fields["academicYearId"]) ?? ""
                    let moduleCount = self.parseInt(fields["moduleCount"]) ?? 0
                    let materialCount = self.parseInt(fields["materialCount"]) ?? 0
                    let order = self.parseInt(fields["order"]) ?? 0
                    
                    return Subject(
                        id: docId,
                        name: name,
                        code: code,
                        departmentId: deptId,
                        academicYearId: yearId,
                        moduleCount: moduleCount,
                        materialCount: materialCount,
                        order: order
                    )
                }.sorted { $0.order < $1.order }
                
                DispatchQueue.main.async { completion(subjects) }
            } catch {
                DispatchQueue.main.async { completion([]) }
            }
        }.resume()
    }
    
    // MARK: - Fetch Live Modules for Subject
    func fetchModules(subjectId: String, completion: @escaping ([Module]) -> Void) {
        guard let url = URL(string: "\(baseUrl)/modules?pageSize=100") else {
            completion([])
            return
        }
        
        URLSession.shared.dataTask(with: url) { data, response, error in
            guard let data = data, error == nil else {
                DispatchQueue.main.async { completion([]) }
                return
            }
            
            do {
                guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let documents = json["documents"] as? [[String: Any]] else {
                    DispatchQueue.main.async { completion([]) }
                    return
                }
                
                let modules: [Module] = documents.compactMap { doc in
                    guard let fields = doc["fields"] as? [String: Any] else { return nil }
                    let docPath = doc["name"] as? String ?? ""
                    let docId = docPath.components(separatedBy: "/").last ?? UUID().uuidString
                    
                    let docSubId = self.parseString(fields["subjectId"]) ?? ""
                    if docSubId != subjectId { return nil }
                    
                    let isActive = self.parseBool(fields["isActive"]) ?? true
                    if !isActive { return nil }
                    
                    let name = self.parseString(fields["name"]) ?? "Module"
                    let desc = self.parseString(fields["description"]) ?? ""
                    let order = self.parseInt(fields["order"]) ?? 0
                    let materialCount = self.parseInt(fields["materialCount"]) ?? 0
                    
                    return Module(
                        id: docId,
                        subjectId: docSubId,
                        name: name,
                        description: desc,
                        order: order,
                        materialCount: materialCount
                    )
                }.sorted { $0.order < $1.order }
                
                DispatchQueue.main.async { completion(modules) }
            } catch {
                DispatchQueue.main.async { completion([]) }
            }
        }.resume()
    }
    
    // MARK: - Fetch Live Materials for Subject
    func fetchMaterials(subjectId: String, section: String, completion: @escaping ([Material]) -> Void) {
        guard let url = URL(string: "\(baseUrl)/materials?pageSize=100") else {
            completion([])
            return
        }
        
        URLSession.shared.dataTask(with: url) { data, response, error in
            guard let data = data, error == nil else {
                DispatchQueue.main.async { completion([]) }
                return
            }
            
            do {
                guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let documents = json["documents"] as? [[String: Any]] else {
                    DispatchQueue.main.async { completion([]) }
                    return
                }
                
                let materials: [Material] = documents.compactMap { doc in
                    guard let fields = doc["fields"] as? [String: Any] else { return nil }
                    let docPath = doc["name"] as? String ?? ""
                    let docId = docPath.components(separatedBy: "/").last ?? UUID().uuidString
                    
                    let docSubId = self.parseString(fields["subjectId"]) ?? ""
                    if docSubId != subjectId { return nil }
                    
                    let isActive = self.parseBool(fields["isActive"]) ?? true
                    if !isActive { return nil }
                    
                    let visibility = self.parseString(fields["visibility"]) ?? "all"
                    let targetSection = self.parseString(fields["section"])
                    if visibility == "section" && targetSection != nil && !targetSection!.isEmpty && targetSection != section {
                        return nil
                    }
                    
                    let title = self.parseString(fields["title"]) ?? "Study Material"
                    let desc = self.parseString(fields["description"]) ?? ""
                    let fileName = self.parseString(fields["fileName"]) ?? "material.pdf"
                    let fileType = self.parseString(fields["fileType"]) ?? "pdf"
                    let fileSize = Int64(self.parseInt(fields["fileSize"]) ?? 0)
                    let downloadUrl = self.parseString(fields["downloadUrl"]) ?? ""
                    let subjectName = self.parseString(fields["subjectName"]) ?? ""
                    let moduleId = self.parseString(fields["moduleId"]) ?? ""
                    let moduleName = self.parseString(fields["moduleName"]) ?? ""
                    
                    return Material(
                        id: docId,
                        title: title,
                        description: desc,
                        fileType: fileType,
                        fileSize: fileSize,
                        fileUrl: downloadUrl,
                        fileName: fileName,
                        subjectId: docSubId,
                        subjectName: subjectName,
                        moduleId: moduleId,
                        moduleName: moduleName,
                        visibility: visibility,
                        section: targetSection,
                        createdAt: Date()
                    )
                }
                
                DispatchQueue.main.async { completion(materials) }
            } catch {
                DispatchQueue.main.async { completion([]) }
            }
        }.resume()
    }
    
    // MARK: - Fetch Live Announcements
    func fetchAnnouncements(section: String, completion: @escaping ([Announcement]) -> Void) {
        guard let url = URL(string: "\(baseUrl)/announcements?pageSize=50") else {
            completion([])
            return
        }
        
        URLSession.shared.dataTask(with: url) { data, response, error in
            guard let data = data, error == nil else {
                DispatchQueue.main.async { completion([]) }
                return
            }
            
            do {
                guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let documents = json["documents"] as? [[String: Any]] else {
                    DispatchQueue.main.async { completion([]) }
                    return
                }
                
                let announcements: [Announcement] = documents.compactMap { doc in
                    guard let fields = doc["fields"] as? [String: Any] else { return nil }
                    let docPath = doc["name"] as? String ?? ""
                    let docId = docPath.components(separatedBy: "/").last ?? UUID().uuidString
                    
                    let isActive = self.parseBool(fields["isActive"]) ?? true
                    if !isActive { return nil }
                    
                    let targetVisibility = self.parseString(fields["targetVisibility"]) ?? "all"
                    let targetSection = self.parseString(fields["targetSection"])
                    if targetVisibility == "section" && targetSection != nil && !targetSection!.isEmpty && targetSection != section {
                        return nil
                    }
                    
                    let title = self.parseString(fields["title"]) ?? "Announcement"
                    let message = self.parseString(fields["message"]) ?? ""
                    let author = self.parseString(fields["authorName"]) ?? "Faculty"
                    
                    return Announcement(
                        id: docId,
                        title: title,
                        message: message,
                        targetType: targetVisibility,
                        targetSection: targetSection,
                        authorName: author,
                        createdAt: Date()
                    )
                }
                
                DispatchQueue.main.async { completion(announcements) }
            } catch {
                DispatchQueue.main.async { completion([]) }
            }
        }.resume()
    }
    
    // MARK: - Upload File to Firebase Storage & Create Material in Firestore
    func uploadMaterial(
        fileData: Data,
        fileName: String,
        fileType: String,
        title: String,
        description: String,
        subjectId: String,
        subjectName: String,
        moduleId: String,
        moduleName: String,
        section: String?,
        uploadedByName: String,
        completion: @escaping (Result<String, Error>) -> Void
    ) {
        let safeName = "\(UUID().uuidString)_\(fileName)"
        let encodedPath = "materials%2F\(safeName)"
        let uploadUrlStr = "https://firebasestorage.googleapis.com/v0/b/\(storageBucket)/o?uploadType=media&name=\(encodedPath)"
        
        guard let uploadUrl = URL(string: uploadUrlStr) else {
            completion(.failure(NSError(domain: "FirebaseService", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid upload URL"])))
            return
        }
        
        var request = URLRequest(url: uploadUrl)
        request.httpMethod = "POST"
        request.setValue("application/octet-stream", forHTTPHeaderField: "Content-Type")
        request.httpBody = fileData
        
        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            guard let self = self else { return }
            if let error = error {
                DispatchQueue.main.async { completion(.failure(error)) }
                return
            }
            
            guard let data = data,
                  let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
                  let downloadToken = json["downloadTokens"] as? String ?? json["name"] as? String else {
                DispatchQueue.main.async {
                    completion(.failure(NSError(domain: "FirebaseService", code: -2, userInfo: [NSLocalizedDescriptionKey: "Failed to parse Storage upload response"])))
                }
                return
            }
            
            let downloadUrl = "https://firebasestorage.googleapis.com/v0/b/\(self.storageBucket)/o/\(encodedPath)?alt=media&token=\(downloadToken)"
            
            self.createFirestoreMaterialDoc(
                title: title,
                description: description,
                fileName: fileName,
                fileType: fileType,
                fileSize: fileData.count,
                downloadUrl: downloadUrl,
                subjectId: subjectId,
                subjectName: subjectName,
                moduleId: moduleId,
                moduleName: moduleName,
                section: section,
                uploadedByName: uploadedByName,
                completion: completion
            )
        }.resume()
    }
    
    private func createFirestoreMaterialDoc(
        title: String,
        description: String,
        fileName: String,
        fileType: String,
        fileSize: Int,
        downloadUrl: String,
        subjectId: String,
        subjectName: String,
        moduleId: String,
        moduleName: String,
        section: String?,
        uploadedByName: String,
        completion: @escaping (Result<String, Error>) -> Void
    ) {
        guard let url = URL(string: "\(baseUrl)/materials") else {
            completion(.failure(NSError(domain: "FirebaseService", code: -3, userInfo: [NSLocalizedDescriptionKey: "Invalid Firestore URL"])))
            return
        }
        
        var fields: [String: Any] = [
            "title": ["stringValue": title],
            "description": ["stringValue": description],
            "fileName": ["stringValue": fileName],
            "fileType": ["stringValue": fileType.lowercased()],
            "fileSize": ["integerValue": "\(fileSize)"],
            "downloadUrl": ["stringValue": downloadUrl],
            "storagePath": ["stringValue": "materials/\(fileName)"],
            "subjectId": ["stringValue": subjectId],
            "subjectName": ["stringValue": subjectName],
            "moduleId": ["stringValue": moduleId],
            "moduleName": ["stringValue": moduleName],
            "visibility": ["stringValue": section == nil || section!.isEmpty ? "all" : "section"],
            "uploadedByName": ["stringValue": uploadedByName],
            "isActive": ["booleanValue": true],
            "version": ["integerValue": "1"]
        ]
        
        if let sec = section, !sec.isEmpty {
            fields["section"] = ["stringValue": sec]
        }
        
        let body: [String: Any] = ["fields": fields]
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        
        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error = error {
                DispatchQueue.main.async { completion(.failure(error)) }
                return
            }
            DispatchQueue.main.async {
                completion(.success("Material uploaded successfully!"))
            }
        }.resume()
    }
    
    // MARK: - Helper Parsing Methods
    private func parseString(_ field: Any?) -> String? {
        guard let dict = field as? [String: Any] else { return nil }
        return dict["stringValue"] as? String
    }
    
    private func parseInt(_ field: Any?) -> Int? {
        guard let dict = field as? [String: Any] else { return nil }
        if let intVal = dict["integerValue"] as? String {
            return Int(intVal)
        }
        if let numVal = dict["integerValue"] as? Int {
            return numVal
        }
        return nil
    }
    
    private func parseBool(_ field: Any?) -> Bool? {
        guard let dict = field as? [String: Any] else { return nil }
        return dict["booleanValue"] as? Bool
    }
}
