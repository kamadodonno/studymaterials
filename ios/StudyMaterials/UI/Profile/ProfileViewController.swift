import UIKit

class ProfileViewController: UIViewController {

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = Theme.background
        title = "Profile"
        
        setupViews()
    }
    
    private func setupViews() {
        let user = SessionManager.shared.currentUser
        let initial = String(user?.name.first ?? "S").uppercased()
        
        let scrollView = UIScrollView()
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)
        
        let contentStack = UIStackView()
        contentStack.axis = .vertical
        contentStack.spacing = 16
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
        
        // Profile Card
        let profileCard = CustomCardView()
        let avatarView = UIView()
        avatarView.backgroundColor = Theme.primaryContainer
        avatarView.layer.cornerRadius = 36
        avatarView.translatesAutoresizingMaskIntoConstraints = false
        
        let initialLabel = UILabel()
        initialLabel.text = initial
        initialLabel.textColor = Theme.onPrimaryContainer
        initialLabel.font = UIFont.systemFont(ofSize: 28, weight: .heavy)
        initialLabel.textAlignment = .center
        initialLabel.translatesAutoresizingMaskIntoConstraints = false
        avatarView.addSubview(initialLabel)
        
        let nameLabel = UILabel()
        nameLabel.text = user?.name ?? "Student Profile"
        nameLabel.textColor = Theme.textPrimary
        nameLabel.font = UIFont.systemFont(ofSize: 20, weight: .bold)
        nameLabel.textAlignment = .center
        
        let metaLabel = UILabel()
        metaLabel.text = "Enrollment: \(user?.enrollmentNumber ?? "") • \(user?.displaySection ?? "")"
        metaLabel.textColor = Theme.primary
        metaLabel.font = UIFont.systemFont(ofSize: 13, weight: .semibold)
        metaLabel.textAlignment = .center
        
        let profileStack = UIStackView(arrangedSubviews: [avatarView, nameLabel, metaLabel])
        profileStack.axis = .vertical
        profileStack.spacing = 10
        profileStack.alignment = .center
        profileStack.translatesAutoresizingMaskIntoConstraints = false
        profileCard.addSubview(profileStack)
        
        NSLayoutConstraint.activate([
            avatarView.widthAnchor.constraint(equalToConstant: 72),
            avatarView.heightAnchor.constraint(equalToConstant: 72),
            initialLabel.centerXAnchor.constraint(equalTo: avatarView.centerXAnchor),
            initialLabel.centerYAnchor.constraint(equalTo: avatarView.centerYAnchor),
            
            profileStack.topAnchor.constraint(equalTo: profileCard.topAnchor, constant: 20),
            profileStack.leadingAnchor.constraint(equalTo: profileCard.leadingAnchor, constant: 16),
            profileStack.trailingAnchor.constraint(equalTo: profileCard.trailingAnchor, constant: -16),
            profileStack.bottomAnchor.constraint(equalTo: profileCard.bottomAnchor, constant: -20)
        ])
        contentStack.addArrangedSubview(profileCard)
        
        // Stats Card
        let statsCard = CustomCardView()
        let count = DownloadManager.shared.getOfflineFileCount()
        let statsLabel = UILabel()
        statsLabel.text = "📥 \(count) Offline Study Files Downloaded"
        statsLabel.textColor = Theme.textPrimary
        statsLabel.font = UIFont.systemFont(ofSize: 14, weight: .bold)
        statsLabel.textAlignment = .center
        statsLabel.translatesAutoresizingMaskIntoConstraints = false
        statsCard.addSubview(statsLabel)
        
        NSLayoutConstraint.activate([
            statsLabel.topAnchor.constraint(equalTo: statsCard.topAnchor, constant: 16),
            statsLabel.leadingAnchor.constraint(equalTo: statsCard.leadingAnchor, constant: 16),
            statsLabel.trailingAnchor.constraint(equalTo: statsCard.trailingAnchor, constant: -16),
            statsLabel.bottomAnchor.constraint(equalTo: statsCard.bottomAnchor, constant: -16)
        ])
        contentStack.addArrangedSubview(statsCard)
        
        // Reset Profile Button
        let resetBtn = UIButton(type: .system)
        resetBtn.setTitle("Reset Profile & Change Section", for: .normal)
        resetBtn.setTitleColor(Theme.error, for: .normal)
        resetBtn.titleLabel?.font = UIFont.systemFont(ofSize: 15, weight: .bold)
        resetBtn.addTarget(self, action: #selector(confirmReset), for: .touchUpInside)
        contentStack.addArrangedSubview(resetBtn)
    }
    
    @objc private func confirmReset() {
        let alert = UIAlertController(title: "Reset Profile?", message: "This will log you out of this session. Downloaded files remain intact.", preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "Reset", style: .destructive, handler: { [weak self] _ in
            SessionManager.shared.clearSession()
            if let window = UIApplication.shared.keyWindow {
                let regVC = RegistrationViewController()
                let nav = UINavigationController(rootViewController: regVC)
                window.rootViewController = nav
                UIView.transition(with: window, duration: 0.3, options: .transitionCrossDissolve, animations: nil, completion: nil)
            }
        }))
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        present(alert, animated: true)
    }
}
