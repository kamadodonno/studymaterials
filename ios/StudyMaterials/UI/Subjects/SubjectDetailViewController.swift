import UIKit

class SubjectDetailViewController: UIViewController, UITableViewDelegate, UITableViewDataSource, UIDocumentInteractionControllerDelegate {

    private let subject: Subject
    private var modules: [Module] = []
    private var materials: [Material] = []
    private let tableView = UITableView(frame: .zero, style: .grouped)
    private var docController: UIDocumentInteractionController?
    
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
        loadSubjectContent()
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
    
    private func loadSubjectContent() {
        // Mock / Initial Data
        materials = [
            Material(id: "m1", title: "General Practice Formulas & Syllabus", description: "Direct practice notes", fileType: "pdf", fileSize: 1048576, fileUrl: "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf", fileName: "formulas.pdf", subjectId: subject.id, subjectName: subject.name, moduleId: "", moduleName: "General", visibility: "all", section: nil, createdAt: Date()),
            Material(id: "m2", title: "Unit 1: Architecture & Fundamentals", description: "Unit 1 Lecture Slides", fileType: "pdf", fileSize: 2097152, fileUrl: "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf", fileName: "unit1_slides.pdf", subjectId: subject.id, subjectName: subject.name, moduleId: "mod1", moduleName: "Unit 1", visibility: "all", section: nil, createdAt: Date()),
            Material(id: "m3", title: "Unit 2: Process Scheduling Notes", description: "Unit 2 Lecture Notes", fileType: "pdf", fileSize: 1572864, fileUrl: "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf", fileName: "unit2_notes.pdf", subjectId: subject.id, subjectName: subject.name, moduleId: "mod2", moduleName: "Unit 2", visibility: "all", section: nil, createdAt: Date())
        ]
        tableView.reloadData()
    }
    
    // MARK: - UITableView
    func numberOfSections(in tableView: UITableView) -> Int { 2 }
    
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        if section == 0 {
            // General direct subject files
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
            label.text = "   📁 GENERAL & PRACTICE NOTES"
        } else {
            label.text = "   📖 MODULES & UNIT FILES"
        }
        return label
    }
    
    func tableView(_ tableView: UITableView, heightForHeaderInSection section: Int) -> CGFloat { 32 }
    
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
            // Download then display
            let alert = UIAlertController(title: "Opening...", message: "Downloading study material", preferredStyle: .alert)
            present(alert, animated: true)
            
            DownloadManager.shared.downloadFile(from: material.fileUrl, materialId: material.id, fileName: material.fileName) { [weak self] result in
                alert.dismiss(animated: true) {
                    switch result {
                    case .success(let url):
                        self?.tableView.reloadData()
                        self?.displayFile(url, title: material.title, fileType: material.fileType)
                    case .failure(let err):
                        let errAlert = UIAlertController(title: "Error", message: err.localizedDescription, preferredStyle: .alert)
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

class MaterialCell: UITableViewCell {
    private let card = CustomCardView()
    private let titleLabel = UILabel()
    private let metaLabel = UILabel()
    private let badgeLabel = UILabel()
    
    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        backgroundColor = .clear
        selectionStyle = .none
        
        titleLabel.textColor = Theme.textPrimary
        titleLabel.font = UIFont.systemFont(ofSize: 15, weight: .bold)
        titleLabel.numberOfLines = 2
        
        metaLabel.textColor = Theme.textSecondary
        metaLabel.font = UIFont.systemFont(ofSize: 12, weight: .medium)
        
        badgeLabel.textColor = Theme.primary
        badgeLabel.font = UIFont.systemFont(ofSize: 11, weight: .bold)
        
        let vStack = UIStackView(arrangedSubviews: [titleLabel, metaLabel, badgeLabel])
        vStack.axis = .vertical
        vStack.spacing = 4
        vStack.translatesAutoresizingMaskIntoConstraints = false
        
        card.addSubview(vStack)
        card.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(card)
        
        NSLayoutConstraint.activate([
            card.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 5),
            card.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 16),
            card.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -16),
            card.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -5),
            
            vStack.topAnchor.constraint(equalTo: card.topAnchor, constant: 12),
            vStack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 14),
            vStack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -14),
            vStack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -12)
        ])
    }
    
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    
    func configure(material: Material, isDownloaded: Bool) {
        titleLabel.text = material.title
        metaLabel.text = "\(material.fileType.uppercased()) • \(material.moduleName)"
        badgeLabel.text = isDownloaded ? "✓ Available Offline" : "☁️ Tap to download"
        badgeLabel.textColor = isDownloaded ? Theme.secondary : Theme.primary
    }
}
