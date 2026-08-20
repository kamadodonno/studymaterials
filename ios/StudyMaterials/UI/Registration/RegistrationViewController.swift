import UIKit

class RegistrationViewController: UIViewController, UIPickerViewDelegate, UIPickerViewDataSource, UITextFieldDelegate {

    private let sections = Constants.defaultSectionNames
    private var selectedSection = "Section 1"
    
    private let titleLabel: UILabel = {
        let label = UILabel()
        label.text = "Study Materials"
        label.textColor = Theme.primary
        label.font = UIFont.systemFont(ofSize: 28, weight: .heavy)
        label.textAlignment = .center
        return label
    }()
    
    private let subtitleLabel: UILabel = {
        let label = UILabel()
        label.text = "Set up your student profile to access study files"
        label.textColor = Theme.textSecondary
        label.font = UIFont.systemFont(ofSize: 14, weight: .medium)
        label.textAlignment = .center
        label.numberOfLines = 0
        return label
    }()
    
    private let nameField: UITextField = {
        let tf = UITextField()
        tf.attributedPlaceholder = NSAttributedString(string: "Full Name (e.g. Rahul Sharma)", attributes: [.foregroundColor: Theme.textSecondary])
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
    
    private let enrollmentField: UITextField = {
        let tf = UITextField()
        tf.attributedPlaceholder = NSAttributedString(string: "Enrollment Number (e.g. PU123456)", attributes: [.foregroundColor: Theme.textSecondary])
        tf.textColor = Theme.textPrimary
        tf.backgroundColor = Theme.surface
        tf.layer.cornerRadius = 12
        tf.layer.borderWidth = 1
        tf.layer.borderColor = Theme.borderOutline
        tf.leftView = UIView(frame: CGRect(x: 0, y: 0, width: 14, height: 48))
        tf.leftViewMode = .always
        tf.autocapitalizationType = .allCharacters
        tf.heightAnchor.constraint(equalToConstant: 48).isActive = true
        return tf
    }()
    
    private let sectionButton: UIButton = {
        let btn = UIButton(type: .system)
        btn.setTitle("Select Section: Section 1", for: .normal)
        btn.setTitleColor(Theme.textPrimary, for: .normal)
        btn.titleLabel?.font = UIFont.systemFont(ofSize: 15, weight: .semibold)
        btn.backgroundColor = Theme.surface
        btn.layer.cornerRadius = 12
        btn.layer.borderWidth = 1
        btn.layer.borderColor = Theme.borderOutline
        btn.contentHorizontalAlignment = .left
        btn.titleEdgeInsets = UIEdgeInsets(top: 0, left: 14, bottom: 0, right: 14)
        btn.heightAnchor.constraint(equalToConstant: 48).isActive = true
        return btn
    }()
    
    private let otherSectionField: UITextField = {
        let tf = UITextField()
        tf.attributedPlaceholder = NSAttributedString(string: "Enter Specific Section Name", attributes: [.foregroundColor: Theme.textSecondary])
        tf.textColor = Theme.textPrimary
        tf.backgroundColor = Theme.surface
        tf.layer.cornerRadius = 12
        tf.layer.borderWidth = 1
        tf.layer.borderColor = Theme.borderOutline
        tf.leftView = UIView(frame: CGRect(x: 0, y: 0, width: 14, height: 48))
        tf.leftViewMode = .always
        tf.heightAnchor.constraint(equalToConstant: 48).isActive = true
        tf.isHidden = true
        return tf
    }()
    
    private let continueButton: UIButton = {
        let btn = UIButton(type: .system)
        btn.setTitle("Continue to Materials", for: .normal)
        btn.setTitleColor(.black, for: .normal)
        btn.titleLabel?.font = UIFont.systemFont(ofSize: 16, weight: .bold)
        btn.backgroundColor = Theme.primary
        btn.layer.cornerRadius = 14
        btn.heightAnchor.constraint(equalToConstant: 50).isActive = true
        return btn
    }()
    
    private let sectionPicker = UIPickerView()

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = Theme.background
        navigationController?.setNavigationBarHidden(true, animated: false)
        
        setupViews()
    }
    
    private func setupViews() {
        let stack = UIStackView(arrangedSubviews: [
            titleLabel,
            subtitleLabel,
            UIView(), // spacing
            nameField,
            enrollmentField,
            sectionButton,
            otherSectionField,
            UIView(), // spacing
            continueButton
        ])
        stack.axis = .vertical
        stack.spacing = 14
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 24),
            stack.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -24),
            stack.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            stack.topAnchor.constraint(greaterThanOrEqualTo: view.safeAreaLayoutGuide.topAnchor, constant: 20)
        ])
        
        sectionButton.addTarget(self, action: #selector(showSectionPicker), for: .touchUpInside)
        continueButton.addTarget(self, action: #selector(handleContinue), for: .touchUpInside)
    }
    
    @objc private func showSectionPicker() {
        let alert = UIAlertController(title: "Select Section", message: "\n\n\n\n\n\n", preferredStyle: .alert)
        sectionPicker.frame = CGRect(x: 0, y: 50, width: 270, height: 140)
        sectionPicker.delegate = self
        sectionPicker.dataSource = self
        alert.view.addSubview(sectionPicker)
        
        alert.addAction(UIAlertAction(title: "Done", style: .default, handler: { [weak self] _ in
            guard let self = self else { return }
            let row = self.sectionPicker.selectedRow(inComponent: 0)
            self.selectedSection = self.sections[row]
            self.sectionButton.setTitle("Select Section: \(self.selectedSection)", for: .normal)
            self.otherSectionField.isHidden = (self.selectedSection != "Other")
        }))
        present(alert, animated: true)
    }
    
    @objc private func handleContinue() {
        guard let name = nameField.text?.trimmingCharacters(in: .whitespacesAndNewlines), !name.isEmpty else {
            showAlert(message: "Please enter your full name.")
            return
        }
        guard let enrollment = enrollmentField.text?.trimmingCharacters(in: .whitespacesAndNewlines), !enrollment.isEmpty else {
            showAlert(message: "Please enter your enrollment number.")
            return
        }
        
        let other = otherSectionField.text?.trimmingCharacters(in: .whitespacesAndNewlines)
        SessionManager.shared.saveUser(name: name, enrollment: enrollment, section: selectedSection, otherSection: other)
        
        let homeVC = HomeViewController()
        navigationController?.setViewControllers([homeVC], animated: true)
    }
    
    private func showAlert(message: String) {
        let alert = UIAlertController(title: "Notice", message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }
    
    // MARK: - UIPickerView
    func numberOfComponents(in pickerView: UIPickerView) -> Int { 1 }
    func pickerView(_ pickerView: UIPickerView, numberOfRowsInComponent component: Int) -> Int { sections.count }
    func pickerView(_ pickerView: UIPickerView, titleForRow row: Int, forComponent component: Int) -> String? { sections[row] }
}
