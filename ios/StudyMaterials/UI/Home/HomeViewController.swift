import UIKit

class HomeViewController: UIViewController, UITableViewDelegate, UITableViewDataSource {

    private var subjects: [Subject] = []
    private var announcements: [Announcement] = []
    private let tableView = UITableView(frame: .zero, style: .grouped)
    
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = Theme.background
        title = "Study Materials"
        navigationController?.setNavigationBarHidden(false, animated: false)
        
        setupTopBarAvatar()
        setupTableView()
        loadData()
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        loadData()
    }
    
    private func setupTopBarAvatar() {
        let user = SessionManager.shared.currentUser
        let initial = String(user?.name.first ?? "S").uppercased()
        
        let avatarButton = UIButton(type: .system)
        avatarButton.frame = CGRect(x: 0, y: 0, width: 36, height: 36)
        avatarButton.backgroundColor = Theme.primaryContainer
        avatarButton.layer.cornerRadius = 18
        avatarButton.layer.borderWidth = 1
        avatarButton.layer.borderColor = Theme.borderOutline
        avatarButton.setTitle(initial, for: .normal)
        avatarButton.setTitleColor(Theme.onPrimaryContainer, for: .normal)
        avatarButton.titleLabel?.font = UIFont.systemFont(ofSize: 16, weight: .heavy)
        avatarButton.addTarget(self, action: #selector(openProfile), for: .touchUpInside)
        
        navigationItem.rightBarButtonItem = UIBarButtonItem(customView: avatarButton)
        
        let reorderBtn = UIBarButtonItem(image: UIImage(named: "arrow.up.arrow.down"), style: .plain, target: self, action: #selector(toggleReorder))
        reorderBtn.title = "Reorder"
        navigationItem.leftBarButtonItem = reorderBtn
    }
    
    private func setupTableView() {
        tableView.backgroundColor = Theme.background
        tableView.separatorStyle = .none
        tableView.delegate = self
        tableView.dataSource = self
        tableView.register(SubjectCell.self, forCellReuseIdentifier: "SubjectCell")
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)
        
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }
    
    private func loadData() {
        guard let user = SessionManager.shared.currentUser else { return }
        
        FirebaseService.shared.fetchAnnouncements(section: user.section) { [weak self] list in
            self?.announcements = list
            self?.tableView.reloadData()
        }
        
        FirebaseService.shared.fetchSubjects { [weak self] list in
            guard let self = self else { return }
            let customOrder = SessionManager.shared.getCustomSubjectOrder()
            if customOrder.isEmpty {
                self.subjects = list
            } else {
                let orderMap = Dictionary(uniqueKeysWithValues: customOrder.enumerated().map { ($0.element, $0.offset) })
                self.subjects = list.sorted {
                    (orderMap[$0.id] ?? Int.max) < (orderMap[$1.id] ?? Int.max)
                }
            }
            self.tableView.reloadData()
        }
    }
    
    @objc private func openProfile() {
        let profileVC = ProfileViewController()
        navigationController?.pushViewController(profileVC, animated: true)
    }
    
    @objc private func toggleReorder() {
        tableView.setEditing(!tableView.isEditing, animated: true)
        navigationItem.leftBarButtonItem?.title = tableView.isEditing ? "Done" : "Reorder"
        
        if !tableView.isEditing {
            let newOrder = subjects.map { $0.id }
            SessionManager.shared.saveCustomSubjectOrder(newOrder)
        }
    }
    
    // MARK: - UITableView
    func numberOfSections(in tableView: UITableView) -> Int { 2 }
    
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return section == 0 ? (announcements.isEmpty ? 0 : 1) : subjects.count
    }
    
    func tableView(_ tableView: UITableView, viewForHeaderInSection section: Int) -> UIView? {
        if section == 1 {
            let label = UILabel()
            label.text = "   📚 STUDY SUBJECTS"
            label.textColor = Theme.textSecondary
            label.font = UIFont.systemFont(ofSize: 13, weight: .bold)
            label.backgroundColor = Theme.background
            return label
        }
        return nil
    }
    
    func tableView(_ tableView: UITableView, heightForHeaderInSection section: Int) -> CGFloat {
        return section == 1 ? 30 : 0
    }
    
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        if indexPath.section == 0 {
            let cell = UITableViewCell(style: .subtitle, reuseIdentifier: "AnnouncementCell")
            cell.backgroundColor = Theme.primaryContainer
            cell.layer.cornerRadius = 12
            cell.layer.borderWidth = 1
            cell.layer.borderColor = Theme.borderOutline
            cell.textLabel?.textColor = Theme.onPrimaryContainer
            cell.textLabel?.font = UIFont.systemFont(ofSize: 15, weight: .bold)
            cell.textLabel?.text = "📢 " + (announcements.first?.title ?? "")
            cell.detailTextLabel?.textColor = Theme.textPrimary
            cell.detailTextLabel?.numberOfLines = 2
            cell.detailTextLabel?.text = announcements.first?.message ?? ""
            return cell
        }
        
        let cell = tableView.dequeueReusableCell(withIdentifier: "SubjectCell", for: indexPath) as! SubjectCell
        let sub = subjects[indexPath.row]
        cell.configure(subject: sub)
        return cell
    }
    
    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        guard indexPath.section == 1 else { return }
        let sub = subjects[indexPath.row]
        let detailVC = SubjectDetailViewController(subject: sub)
        navigationController?.pushViewController(detailVC, animated: true)
    }
    
    func tableView(_ tableView: UITableView, canMoveRowAt indexPath: IndexPath) -> Bool {
        return indexPath.section == 1
    }
    
    func tableView(_ tableView: UITableView, moveRowAt sourceIndexPath: IndexPath, to destinationIndexPath: IndexPath) {
        guard sourceIndexPath.section == 1 && destinationIndexPath.section == 1 else { return }
        let moved = subjects.remove(at: sourceIndexPath.row)
        subjects.insert(moved, at: destinationIndexPath.row)
        let newOrder = subjects.map { $0.id }
        SessionManager.shared.saveCustomSubjectOrder(newOrder)
    }
}

class SubjectCell: UITableViewCell {
    private let card = CustomCardView()
    private let titleLabel = UILabel()
    private let codeLabel = UILabel()
    private let metaLabel = UILabel()
    
    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        backgroundColor = .clear
        selectionStyle = .none
        
        titleLabel.textColor = Theme.textPrimary
        titleLabel.font = UIFont.systemFont(ofSize: 16, weight: .bold)
        
        codeLabel.textColor = Theme.primary
        codeLabel.font = UIFont.systemFont(ofSize: 12, weight: .bold)
        
        metaLabel.textColor = Theme.textSecondary
        metaLabel.font = UIFont.systemFont(ofSize: 12, weight: .medium)
        
        let vStack = UIStackView(arrangedSubviews: [codeLabel, titleLabel, metaLabel])
        vStack.axis = .vertical
        vStack.spacing = 3
        vStack.translatesAutoresizingMaskIntoConstraints = false
        
        card.addSubview(vStack)
        card.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(card)
        
        NSLayoutConstraint.activate([
            card.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 6),
            card.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 16),
            card.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -16),
            card.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -6),
            
            vStack.topAnchor.constraint(equalTo: card.topAnchor, constant: 14),
            vStack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            vStack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            vStack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -14)
        ])
    }
    
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    
    func configure(subject: Subject) {
        codeLabel.text = subject.code
        titleLabel.text = subject.name
        metaLabel.text = "\(subject.moduleCount) Modules • \(subject.materialCount) Study Materials"
    }
}
