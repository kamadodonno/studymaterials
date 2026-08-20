import Foundation

class DownloadManager {
    static let shared = DownloadManager()
    
    private init() {
        createDownloadDirectoryIfNeeded()
    }
    
    private var downloadDirectoryUrl: URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        return docs.appendingPathComponent("study_materials", isDirectory: true)
    }
    
    private func createDownloadDirectoryIfNeeded() {
        try? FileManager.default.createDirectory(at: downloadDirectoryUrl, withIntermediateDirectories: true, attributes: nil)
    }
    
    func getLocalFileUrl(materialId: String, fileName: String) -> URL {
        let safeName = "\(materialId)_\(fileName)"
        return downloadDirectoryUrl.appendingPathComponent(safeName)
    }
    
    func isMaterialDownloaded(materialId: String, fileName: String) -> Bool {
        let fileUrl = getLocalFileUrl(materialId: materialId, fileName: fileName)
        return FileManager.default.fileExists(atPath: fileUrl.path)
    }
    
    func downloadFile(from urlString: String, materialId: String, fileName: String, completion: @escaping (Result<URL, Error>) -> Void) {
        guard let url = URL(string: urlString) else {
            completion(.failure(NSError(domain: "DownloadManager", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid file URL"])))
            return
        }
        
        let destinationUrl = getLocalFileUrl(materialId: materialId, fileName: fileName)
        
        let task = URLSession.shared.downloadTask(with: url) { (tempLocalUrl, response, error) in
            if let error = error {
                DispatchQueue.main.async { completion(.failure(error)) }
                return
            }
            guard let tempLocalUrl = tempLocalUrl else {
                DispatchQueue.main.async {
                    completion(.failure(NSError(domain: "DownloadManager", code: -2, userInfo: [NSLocalizedDescriptionKey: "Download failed"])))
                }
                return
            }
            
            do {
                if FileManager.default.fileExists(atPath: destinationUrl.path) {
                    try FileManager.default.removeItem(at: destinationUrl)
                }
                try FileManager.default.copyItem(at: tempLocalUrl, to: destinationUrl)
                DispatchQueue.main.async {
                    completion(.success(destinationUrl))
                }
            } catch {
                DispatchQueue.main.async { completion(.failure(error)) }
            }
        }
        task.resume()
    }
    
    func getOfflineFileCount() -> Int {
        let files = (try? FileManager.default.contentsOfDirectory(atPath: downloadDirectoryUrl.path)) ?? []
        return files.count
    }
}
