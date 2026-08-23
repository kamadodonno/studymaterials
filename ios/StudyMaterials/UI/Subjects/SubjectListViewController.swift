import UIKit

class SubjectListViewController: UIViewController, UITableViewDelegate, UITableViewDataSource, UISearchBarDelegate {

    private var allSubjects: [Subject] = []
    private var filteredSubjects: [Subject] = []
    private let tableView = UITableView(frame: .zero, style: .grouped)
    private let searchBar = UISearchBar()
    private let loadingIndicator = UIActivityIndicatorView(style: .whiteLarge)
    
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = Theme.background
        title = "Subjects"
        
        setupSearchBar()
        setupTableView()
        setupLoadingIndicator()
        loadSubjects()
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        loadSubjects()
    }
    
    private func setupSearchBar() {
        searchBar.placeholder = "Search subjects by name or code..."
        searchBar.barTintColor = Theme.background
        searchBar.backgroundColor = Theme.background
        searchBar.delegate = self
        searchBar.searchBarStyle = .minimal
        if let tf = searchBar.value(forKey: "searchField") as? UITextField {
            tf.textColor = Theme.textPrimary
            tf.backgroundColor = Theme.surface
        }
        
        let reorderBtn = UIBarButtonItem(title: "Reorder", style: .plain, target: self, action: #selector(toggleReorder))
        navigationItem.rightBarButtonItem = reorderBtn
    }
    
    private func setupTableView() {
        tableView.backgroundColor = Theme.background
        tableView.separatorStyle = .none
        tableView.delegate = self
        tableView.dataSource = self
        tableView.tableHeaderView = searchBar
        searchBar.sizeToFit()
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
    
    private func setupLoadingIndicator() {
        loadingIndicator.color = Theme.primary
        loadingIndicator.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(loadingIndicator)
        
        NSLayoutConstraint.activate([
            loadingIndicator.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            loadingIndicator.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])
    }
    
    private func loadSubjects() {
        loadingIndicator.startAnimating()
        FirebaseService.shared.fetchSubjects { [weak self] list in
            guard let self = self else { return }
            self.loadingIndicator.stopAnimating()
            
            let customOrder = SessionManager.shared.getCustomSubjectOrder()
            if customOrder.isEmpty {
                self.allSubjects = list
            } else {
                let orderMap = Dictionary(uniqueKeysWithValues: customOrder.enumerated().map { ($0.element, $0.offset) })
                self.allSubjects = list.sorted {
                    (orderMap[$0.id] ?? Int.max) < (orderMap[$1.id] ?? Int.max)
                }
            }
            self.filterSubjects()
        }
    }
    
    private func filterSubjects() {
        let query = (searchBar.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if query.isEmpty {
            filteredSubjects = allSubjects
        } else {
            filteredSubjects = allSubjects.filter {
                $0.name.lowercased().contains(query) || $0.code.lowercased().contains(query)
            }
        }
        tableView.reloadData()
    }
    
    @objc private func toggleReorder() {
        tableView.setEditing(!tableView.isEditing, animated: true)
        navigationItem.rightBarButtonItem?.title = tableView.isEditing ? "Done" : "Reorder"
        
        if !tableView.isEditing {
            let newOrder = allSubjects.map { $0.id }
            SessionManager.shared.saveCustomSubjectOrder(newOrder)
        }
    }
    
    // MARK: - UISearchBarDelegate
    func searchBar(_ searchBar: UISearchBar, textDidChange searchText: String) {
        filterSubjects()
    }
    
    // MARK: - UITableView
    func numberOfSections(in tableView: UITableView) -> Int { 1 }
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { filteredSubjects.count }
    
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "SubjectCell", for: indexPath) as! SubjectCell
        let sub = filteredSubjects[indexPath.row]
        cell.configure(subject: sub)
        return cell
    }
    
    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let sub = filteredSubjects[indexPath.row]
        let detailVC = SubjectDetailViewController(subject: sub)
        navigationController?.pushViewController(detailVC, animated: true)
    }
    
    func tableView(_ tableView: UITableView, canMoveRowAt indexPath: IndexPath) -> Bool {
        return searchBar.text?.isEmpty ?? true
    }
    
    func tableView(_ tableView: UITableView, moveRowAt sourceIndexPath: IndexPath, to destinationIndexPath: IndexPath) {
        let moved = allSubjects.remove(at: sourceIndexPath.row)
        allSubjects.insert(moved, at: destinationIndexPath.row)
        filteredSubjects = allSubjects
        let newOrder = allSubjects.map { $0.id }
        SessionManager.shared.saveCustomSubjectOrder(newOrder)
    }
}
