import UIKit

class SubjectDetailViewController: UIViewController, UITableViewDelegate, UITableViewDataSource, UIDocumentInteractionControllerDelegate {

    private let subject: Subject
    private var modules: [Module] = []
    private var materials: [Material] = []
    private let tableView = UITableView(frame: .zero, style: .grouped)
    private var docController: UIDocumentInteractionController?
    private let loadingIndicator = UIActivityIndicatorView(style: .whiteLarge)
    
    init(subject: Subject) {
        self.subject = subject
        super.init(nibName: nil, bundle: nil)
    }
    
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = Theme.background
        title = subject.name
        
        setupTableView()
        setupLoadingIndicator()
        loadLiveSubjectContent()
    }
    
    private func setupTableView() {
        tableView.backgroundColor = Theme.background
        tableView.separatorStyle = .none
        tableView.delegate = self
        tableView.dataSource = self
        tableView.register(MaterialCell.self, forCellReuseIdentifier: "MaterialCell")
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)
        
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }
    
    private func setupLoadingIndicator() {
        loadingIndicator.color = Theme.primary
        loadingIndicator.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(loadingIndicator)
        
        NSLayoutConstraint.activate([
            loadingIndicator.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            loadingIndicator.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])
    }
    
    private func loadLiveSubjectContent() {
        loadingIndicator.startAnimating()
        let userSection = SessionManager.shared.currentUser?.section ?? ""
        
        FirebaseService.shared.fetchMaterials(subjectId: subject.id, section: userSection) { [weak self] list in
            guard let self = self else { return }
            self.loadingIndicator.stopAnimating()
            self.materials = list
            self.tableView.reloadData()
        }
        
        FirebaseService.shared.fetchModules(subjectId: subject.id) { [weak self] list in
            self?.modules = list
            self?.tableView.reloadData()
        }
    }
    
    // MARK: - UITableView
    func numberOfSections(in tableView: UITableView) -> Int { 2 }
    
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        if section == 0 {
            // General direct subject files (where moduleId is blank)
            return materials.filter { $0.isDirectSubjectFile }.count
        } else {
            // Module files
            return materials.filter { !$0.isDirectSubjectFile }.count
        }
    }
    
    func tableView(_ tableView: UITableView, viewForHeaderInSection section: Int) -> UIView? {
        let label = UILabel()
        label.textColor = Theme.primary
        label.font = UIFont.systemFont(ofSize: 13, weight: .bold)
        label.backgroundColor = Theme.background
        if section == 0 {
            let count = materials.filter { $0.isDirectSubjectFile }.count
            if count == 0 { return nil }
            label.text = "   📁 GENERAL & PRACTICE NOTES (\(count))"
        } else {
            let count = materials.filter { !$0.isDirectSubjectFile }.count
            if count == 0 { return nil }
            label.text = "   📖 MODULES & UNIT FILES (\(count))"
        }
        return label
    }
    
    func tableView(_ tableView: UITableView, heightForHeaderInSection section: Int) -> CGFloat {
        if section == 0 {
            return materials.filter { $0.isDirectSubjectFile }.isEmpty ? 0 : 32
        } else {
            return materials.filter { !$0.isDirectSubjectFile }.isEmpty ? 0 : 32
        }
    }
    
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "MaterialCell", for: indexPath) as! MaterialCell
        let list = (indexPath.section == 0) ? materials.filter { $0.isDirectSubjectFile } : materials.filter { !$0.isDirectSubjectFile }
        let mat = list[indexPath.row]
        let isDownloaded = DownloadManager.shared.isMaterialDownloaded(materialId: mat.id, fileName: mat.fileName)
        cell.configure(material: mat, isDownloaded: isDownloaded)
        return cell
    }
    
    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let list = (indexPath.section == 0) ? materials.filter { $0.isDirectSubjectFile } : materials.filter { !$0.isDirectSubjectFile }
        let mat = list[indexPath.row]
        openMaterial(mat)
    }
    
    private func openMaterial(_ material: Material) {
        let localUrl = DownloadManager.shared.getLocalFileUrl(materialId: material.id, fileName: material.fileName)
        
        if FileManager.default.fileExists(atPath: localUrl.path) {
            displayFile(localUrl, title: material.title, fileType: material.fileType)
        } else {
            guard !material.fileUrl.isEmpty else {
                let errAlert = UIAlertController(title: "Notice", message: "Download URL for this material is not available yet.", preferredStyle: .alert)
                errAlert.addAction(UIAlertAction(title: "OK", style: .default))
                present(errAlert, animated: true)
                return
            }
            
            let alert = UIAlertController(title: "Opening...", message: "Downloading \(material.fileName)", preferredStyle: .alert)
            present(alert, animated: true)
            
            DownloadManager.shared.downloadFile(from: material.fileUrl, materialId: material.id, fileName: material.fileName) { [weak self] result in
                alert.dismiss(animated: true) {
                    switch result {
                    case .success(let url):
                        self?.tableView.reloadData()
                        self?.displayFile(url, title: material.title, fileType: material.fileType)
                    case .failure(let err):
                        let errAlert = UIAlertController(title: "Download Failed", message: err.localizedDescription, preferredStyle: .alert)
                        errAlert.addAction(UIAlertAction(title: "OK", style: .default))
                        self?.present(errAlert, animated: true)
                    }
                }
            }
        }
    }
    
    private func displayFile(_ url: URL, title: String, fileType: String) {
        if fileType.lowercased() == "pdf" {
            let pdfVC = PdfViewerViewController(fileUrl: url, title: title)
            navigationController?.pushViewController(pdfVC, animated: true)
        } else {
            docController = UIDocumentInteractionController(url: url)
            docController?.delegate = self
            docController?.presentPreview(animated: true)
        }
    }
    
    func documentInteractionControllerViewControllerForPreview(_ controller: UIDocumentInteractionController) -> UIViewController {
        return self
    }
}
