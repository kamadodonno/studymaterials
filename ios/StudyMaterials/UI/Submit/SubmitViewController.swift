import UIKit
import MobileCoreServices

class SubmitViewController: UIViewController, UIDocumentPickerDelegate, UIPickerViewDelegate, UIPickerViewDataSource, UITextFieldDelegate {

    private var subjects: [Subject] = []
    private var modules: [Module] = []
    
    private var selectedSubject: Subject?
    private var selectedModule: Module?
    private var selectedFileData: Data?
    private var selectedFileName: String?
    private var selectedFileType: String?
    
    private let scrollView = UIScrollView()
    private let contentStack = UIStackView()
    
    private let titleLabel: UILabel = {
        let label = UILabel()
        label.text = "Upload Study Material"
        label.textColor = Theme.textPrimary
        label.font = UIFont.systemFont(ofSize: 22, weight: .bold)
        return label
    }()
    
    private let subtitleLabel: UILabel = {
        let label = UILabel()
        label.text = "Upload notes, PDFs, or slides. Syncs directly to all Android and iOS students."
        label.textColor = Theme.textSecondary
        label.font = UIFont.systemFont(ofSize: 13, weight: .medium)
        label.numberOfLines = 0
        return label
    }()
    
    private let filePickerCard = CustomCardView()
    private let filePickerLabel: UILabel = {
        let label = UILabel()
        label.text = "📄 Tap to select file (PDF, PPT, DOCX)"
        label.textColor = Theme.primary
        label.font = UIFont.systemFont(ofSize: 14, weight: .bold)
        label.textAlignment = .center
        return label
    }()
    
    private let materialTitleField: UITextField = {
        let tf = UITextField()
        tf.attributedPlaceholder = NSAttributedString(string: "Material Title (e.g. Unit 1 Class Notes)", attributes: [.foregroundColor: Theme.textSecondary])
        tf.textColor = Theme.textPrimary
        tf.backgroundColor = Theme.surface
        tf.layer.cornerRadius = 12
        tf.layer.borderWidth = 1
        tf.layer.borderColor = Theme.borderOutline
        tf.leftView = UIView(frame: CGRect(x: 0, y: 0, width: 14, height: 48))
        tf.leftViewMode = .always
        tf.heightAnchor.constraint(equalToConstant: 48).isActive = true
        return tf
    }()
    
    private let materialDescField: UITextField = {
        let tf = UITextField()
        tf.attributedPlaceholder = NSAttributedString(string: "Brief Description / Topic Summary", attributes: [.foregroundColor: Theme.textSecondary])
        tf.textColor = Theme.textPrimary
        tf.backgroundColor = Theme.surface
        tf.layer.cornerRadius = 12
        tf.layer.borderWidth = 1
        tf.layer.borderColor = Theme.borderOutline
        tf.leftView = UIView(frame: CGRect(x: 0, y: 0, width: 14, height: 48))
        tf.leftViewMode = .always
        tf.heightAnchor.constraint(equalToConstant: 48).isActive = true
        return tf
    }()
    
    private let subjectButton: UIButton = {
        let btn = UIButton(type: .system)
        btn.setTitle("Select Subject: Choose Subject", for: .normal)
        btn.setTitleColor(Theme.textPrimary, for: .normal)
        btn.titleLabel?.font = UIFont.systemFont(ofSize: 14, weight: .semibold)
        btn.backgroundColor = Theme.surface
        btn.layer.cornerRadius = 12
        btn.layer.borderWidth = 1
        btn.layer.borderColor = Theme.borderOutline
        btn.contentHorizontalAlignment = .left
        btn.titleEdgeInsets = UIEdgeInsets(top: 0, left: 14, bottom: 0, right: 14)
        btn.heightAnchor.constraint(equalToConstant: 48).isActive = true
        return btn
    }()
    
    private let moduleButton: UIButton = {
        let btn = UIButton(type: .system)
        btn.setTitle("Folder / Module: General Subject Notes", for: .normal)
        btn.setTitleColor(Theme.textPrimary, for: .normal)
        btn.titleLabel?.font = UIFont.systemFont(ofSize: 14, weight: .semibold)
        btn.backgroundColor = Theme.surface
        btn.layer.cornerRadius = 12
        btn.layer.borderWidth = 1
        btn.layer.borderColor = Theme.borderOutline
        btn.contentHorizontalAlignment = .left
        btn.titleEdgeInsets = UIEdgeInsets(top: 0, left: 14, bottom: 0, right: 14)
        btn.heightAnchor.constraint(equalToConstant: 48).isActive = true
        return btn
    }()
    
    private let uploadButton: UIButton = {
        let btn = UIButton(type: .system)
        btn.setTitle("Upload Material to Cloud", for: .normal)
        btn.setTitleColor(.black, for: .normal)
        btn.titleLabel?.font = UIFont.systemFont(ofSize: 16, weight: .bold)
        btn.backgroundColor = Theme.primary
        btn.layer.cornerRadius = 14
        btn.heightAnchor.constraint(equalToConstant: 50).isActive = true
        return btn
    }()
    
    private let loadingIndicator = UIActivityIndicatorView(style: .whiteLarge)
    private let picker = UIPickerView()
    private var isPickingSubject = true

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = Theme.background
        title = "Upload"
        
        setupViews()
        loadSubjects()
    }
    
    private func setupViews() {
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)
        
        contentStack.axis = .vertical
        contentStack.spacing = 14
        contentStack.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(contentStack)
        
        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            
            contentStack.topAnchor.constraint(equalTo: scrollView.topAnchor, constant: 16),
            contentStack.leadingAnchor.constraint(equalTo: scrollView.leadingAnchor, constant: 16),
            contentStack.trailingAnchor.constraint(equalTo: scrollView.trailingAnchor, constant: -16),
            contentStack.bottomAnchor.constraint(equalTo: scrollView.bottomAnchor, constant: -24),
            contentStack.widthAnchor.constraint(equalTo: scrollView.widthAnchor, constant: -32)
        ])
        
        contentStack.addArrangedSubview(titleLabel)
        contentStack.addArrangedSubview(subtitleLabel)
        
        // File picker card
        let tap = UITapGestureRecognizer(target: self, action: #selector(openDocumentPicker))
        filePickerCard.addGestureRecognizer(tap)
        filePickerCard.isUserInteractionEnabled = true
        filePickerCard.addSubview(filePickerLabel)
        filePickerLabel.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            filePickerLabel.topAnchor.constraint(equalTo: filePickerCard.topAnchor, constant: 20),
            filePickerLabel.bottomAnchor.constraint(equalTo: filePickerCard.bottomAnchor, constant: -20),
            filePickerLabel.leadingAnchor.constraint(equalTo: filePickerCard.leadingAnchor, constant: 16),
            filePickerLabel.trailingAnchor.constraint(equalTo: filePickerCard.trailingAnchor, constant: -16)
        ])
        contentStack.addArrangedSubview(filePickerCard)
        
        contentStack.addArrangedSubview(materialTitleField)
        contentStack.addArrangedSubview(materialDescField)
        contentStack.addArrangedSubview(subjectButton)
        contentStack.addArrangedSubview(moduleButton)
        contentStack.addArrangedSubview(uploadButton)
        
        subjectButton.addTarget(self, action: #selector(showSubjectPicker), for: .touchUpInside)
        moduleButton.addTarget(self, action: #selector(showModulePicker), for: .touchUpInside)
        uploadButton.addTarget(self, action: #selector(handleUpload), for: .touchUpInside)
        
        loadingIndicator.color = Theme.primary
        loadingIndicator.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(loadingIndicator)
        NSLayoutConstraint.activate([
            loadingIndicator.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            loadingIndicator.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])
    }
    
    private func loadSubjects() {
        FirebaseService.shared.fetchSubjects { [weak self] list in
            self?.subjects = list
            if let first = list.first {
                self?.selectedSubject = first
                self?.subjectButton.setTitle("Select Subject: \(first.name) (\(first.code))", for: .normal)
                self?.loadModules(for: first.id)
            }
        }
    }
    
    private func loadModules(for subjectId: String) {
        FirebaseService.shared.fetchModules(subjectId: subjectId) { [weak self] list in
            self?.modules = list
            self?.selectedModule = nil
            self?.moduleButton.setTitle("Folder / Module: General Subject Notes", for: .normal)
        }
    }
    
    @objc private func openDocumentPicker() {
        let types = ["com.adobe.pdf", "org.openxmlformats.openxmlformats-officedocument.presentationml.presentation", "com.microsoft.powerpoint.ppt", "org.openxmlformats.openxmlformats-officedocument.wordprocessingml.document", "com.microsoft.word.doc", "public.data"]
        let docPicker = UIDocumentPickerViewController(documentTypes: types, in: .import)
        docPicker.delegate = self
        docPicker.allowsMultipleSelection = false
        present(docPicker, animated: true)
    }
    
    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        guard let url = urls.first else { return }
        
        let fileName = url.lastPathComponent
        let ext = url.pathExtension.lowercased()
        
        if let data = try? Data(contentsOf: url) {
            selectedFileData = data
            selectedFileName = fileName
            selectedFileType = ext
            
            let sizeMb = String(format: "%.1f MB", Double(data.count) / 1048576.0)
            filePickerLabel.text = "✓ \(fileName) (\(sizeMb))"
            filePickerLabel.textColor = Theme.secondary
            
            if materialTitleField.text?.isEmpty ?? true {
                materialTitleField.text = url.deletingPathExtension().lastPathComponent
            }
        }
    }
    
    @objc private func showSubjectPicker() {
        isPickingSubject = true
        showModalPicker(title: "Select Subject")
    }
    
    @objc private func showModulePicker() {
        isPickingSubject = false
        showModalPicker(title: "Select Module / General Note")
    }
    
    private func showModalPicker(title: String) {
        let alert = UIAlertController(title: title, message: "\n\n\n\n\n\n", preferredStyle: .alert)
        picker.frame = CGRect(x: 0, y: 50, width: 270, height: 140)
        picker.delegate = self
        picker.dataSource = self
        alert.view.addSubview(picker)
        
        alert.addAction(UIAlertAction(title: "Done", style: .default, handler: { [weak self] _ in
            guard let self = self else { return }
            let row = self.picker.selectedRow(inComponent: 0)
            if self.isPickingSubject {
                if row < self.subjects.count {
                    let sub = self.subjects[row]
                    self.selectedSubject = sub
                    self.subjectButton.setTitle("Select Subject: \(sub.name) (\(sub.code))", for: .normal)
                    self.loadModules(for: sub.id)
                }
            } else {
                if row == 0 {
                    self.selectedModule = nil
                    self.moduleButton.setTitle("Folder / Module: General Subject Notes", for: .normal)
                } else if row - 1 < self.modules.count {
                    let mod = self.modules[row - 1]
                    self.selectedModule = mod
                    self.moduleButton.setTitle("Folder / Module: \(mod.name)", for: .normal)
                }
            }
        }))
        present(alert, animated: true)
    }
    
    @objc private func handleUpload() {
        guard let data = selectedFileData, let fileName = selectedFileName, let fileType = selectedFileType else {
            showAlert(title: "Missing File", message: "Please tap to select a PDF, PPT, or DOC file first.")
            return
        }
        
        guard let title = materialTitleField.text?.trimmingCharacters(in: .whitespacesAndNewlines), !title.isEmpty else {
            showAlert(title: "Missing Title", message: "Please enter a title for this material.")
            return
        }
        
        guard let sub = selectedSubject else {
            showAlert(title: "Missing Subject", message: "Please select a subject.")
            return
        }
        
        let desc = materialDescField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let modId = selectedModule?.id ?? ""
        let modName = selectedModule?.name ?? "General"
        let user = SessionManager.shared.currentUser
        
        loadingIndicator.startAnimating()
        uploadButton.isEnabled = false
        
        FirebaseService.shared.uploadMaterial(
            fileData: data,
            fileName: fileName,
            fileType: fileType,
            title: title,
            description: desc,
            subjectId: sub.id,
            subjectName: sub.name,
            moduleId: modId,
            moduleName: modName,
            section: user?.section,
            uploadedByName: user?.name ?? "Faculty",
            completion: { [weak self] result in
                guard let self = self else { return }
                self.loadingIndicator.stopAnimating()
                self.uploadButton.isEnabled = true
                
                switch result {
                case .success:
                    let successAlert = UIAlertController(title: "Upload Successful!", message: "Material is now live on both Android and iOS.", preferredStyle: .alert)
                    successAlert.addAction(UIAlertAction(title: "OK", style: .default, handler: { _ in
                        self.materialTitleField.text = ""
                        self.materialDescField.text = ""
                        self.selectedFileData = nil
                        self.selectedFileName = nil
                        self.filePickerLabel.text = "📄 Tap to select file (PDF, PPT, DOCX)"
                        self.filePickerLabel.textColor = Theme.primary
                    }))
                    self.present(successAlert, animated: true)
                case .failure(let err):
                    self.showAlert(title: "Upload Error", message: err.localizedDescription)
                }
            }
        )
    }
    
    private func showAlert(title: String, message: String) {
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }
    
    // MARK: - UIPickerView
    func numberOfComponents(in pickerView: UIPickerView) -> Int { 1 }
    func pickerView(_ pickerView: UIPickerView, numberOfRowsInComponent component: Int) -> Int {
        return isPickingSubject ? subjects.count : (modules.count + 1)
    }
    func pickerView(_ pickerView: UIPickerView, titleForRow row: Int, forComponent component: Int) -> String? {
        if isPickingSubject {
            return "\(subjects[row].name) (\(subjects[row].code))"
        } else {
            return row == 0 ? "📁 General Subject Notes" : "📖 \(modules[row - 1].name)"
        }
    }
}
