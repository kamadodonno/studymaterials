import UIKit

class DownloadsViewController: UIViewController, UITableViewDelegate, UITableViewDataSource, UIDocumentInteractionControllerDelegate {

    private var downloadedFiles: [URL] = []
    private let tableView = UITableView(frame: .zero, style: .grouped)
    private let emptyLabel = UILabel()
    private var docController: UIDocumentInteractionController?
    
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = Theme.background
        title = "Downloads"
        
        setupTableView()
        setupEmptyLabel()
        loadDownloadedFiles()
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        loadDownloadedFiles()
    }
    
    private func setupTableView() {
        tableView.backgroundColor = Theme.background
        tableView.separatorStyle = .none
        tableView.delegate = self
        tableView.dataSource = self
        tableView.register(DownloadedCell.self, forCellReuseIdentifier: "DownloadedCell")
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)
        
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }
    
    private func setupEmptyLabel() {
        emptyLabel.text = "No offline materials downloaded yet.\nOpen any material to download it."
        emptyLabel.textColor = Theme.textSecondary
        emptyLabel.font = UIFont.systemFont(ofSize: 14, weight: .medium)
        emptyLabel.textAlignment = .center
        emptyLabel.numberOfLines = 0
        emptyLabel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(emptyLabel)
        
        NSLayoutConstraint.activate([
            emptyLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            emptyLabel.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            emptyLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 32),
            emptyLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -32)
        ])
    }
    
    private func loadDownloadedFiles() {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let dir = docs.appendingPathComponent("study_materials", isDirectory: true)
        
        let urls = (try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)) ?? []
        downloadedFiles = urls.sorted { $0.lastPathComponent < $1.lastPathComponent }
        
        emptyLabel.isHidden = !downloadedFiles.isEmpty
        tableView.reloadData()
    }
    
    // MARK: - UITableView
    func numberOfSections(in tableView: UITableView) -> Int { 1 }
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { downloadedFiles.count }
    
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "DownloadedCell", for: indexPath) as! DownloadedCell
        let url = downloadedFiles[indexPath.row]
        cell.configure(url: url)
        return cell
    }
    
    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let url = downloadedFiles[indexPath.row]
        let ext = url.pathExtension.lowercased()
        
        if ext == "pdf" {
            let pdfVC = PdfViewerViewController(fileUrl: url, title: url.lastPathComponent)
            navigationController?.pushViewController(pdfVC, animated: true)
        } else {
            docController = UIDocumentInteractionController(url: url)
            docController?.delegate = self
            docController?.presentPreview(animated: true)
        }
    }
    
    func tableView(_ tableView: UITableView, commit editingStyle: UITableViewCell.EditingStyle, forRowAt indexPath: IndexPath) {
        if editingStyle == .delete {
            let url = downloadedFiles[indexPath.row]
            try? FileManager.default.removeItem(at: url)
            downloadedFiles.remove(at: indexPath.row)
            tableView.deleteRows(at: [indexPath], with: .fade)
            emptyLabel.isHidden = !downloadedFiles.isEmpty
        }
    }
    
    func documentInteractionControllerViewControllerForPreview(_ controller: UIDocumentInteractionController) -> UIViewController {
        return self
    }
}

class DownloadedCell: UITableViewCell {
    private let card = CustomCardView()
    private let nameLabel = UILabel()
    private let metaLabel = UILabel()
    
    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        backgroundColor = .clear
        selectionStyle = .none
        
        nameLabel.textColor = Theme.textPrimary
        nameLabel.font = UIFont.systemFont(ofSize: 15, weight: .bold)
        nameLabel.numberOfLines = 2
        
        metaLabel.textColor = Theme.secondary
        metaLabel.font = UIFont.systemFont(ofSize: 12, weight: .semibold)
        
        let stack = UIStackView(arrangedSubviews: [nameLabel, metaLabel])
        stack.axis = .vertical
        stack.spacing = 4
        stack.translatesAutoresizingMaskIntoConstraints = false
        
        card.addSubview(stack)
        card.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(card)
        
        NSLayoutConstraint.activate([
            card.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 5),
            card.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 16),
            card.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -16),
            card.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -5),
            
            stack.topAnchor.constraint(equalTo: card.topAnchor, constant: 12),
            stack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 14),
            stack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -14),
            stack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -12)
        ])
    }
    
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    
    func configure(url: URL) {
        let fullName = url.lastPathComponent
        // Clean display name by stripping UUID prefix if present
        let cleanName = fullName.components(separatedBy: "_").dropFirst().joined(separator: "_")
        nameLabel.text = cleanName.isEmpty ? fullName : cleanName
        metaLabel.text = "✓ Offline • \(url.pathExtension.uppercased())"
    }
}
