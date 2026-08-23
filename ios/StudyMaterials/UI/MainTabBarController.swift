import UIKit

class MainTabBarController: UITabBarController {

    override func viewDidLoad() {
        super.viewDidLoad()
        setupTabs()
        setupAppearance()
    }
    
    private func setupTabs() {
        let homeVC = UINavigationController(rootViewController: HomeViewController())
        homeVC.tabBarItem = UITabBarItem(title: "Home", image: nil, selectedImage: nil)
        homeVC.tabBarItem.title = "Home"
        
        let subjectsVC = UINavigationController(rootViewController: SubjectListViewController())
        subjectsVC.tabBarItem = UITabBarItem(title: "Subjects", image: nil, selectedImage: nil)
        subjectsVC.tabBarItem.title = "Subjects"
        
        let downloadsVC = UINavigationController(rootViewController: DownloadsViewController())
        downloadsVC.tabBarItem = UITabBarItem(title: "Downloads", image: nil, selectedImage: nil)
        downloadsVC.tabBarItem.title = "Downloads"
        
        let submitVC = UINavigationController(rootViewController: SubmitViewController())
        submitVC.tabBarItem = UITabBarItem(title: "Submit", image: nil, selectedImage: nil)
        submitVC.tabBarItem.title = "Submit"
        
        viewControllers = [homeVC, subjectsVC, downloadsVC, submitVC]
    }
    
    private func setupAppearance() {
        tabBar.barTintColor = Theme.background
        tabBar.tintColor = Theme.primary
        tabBar.unselectedItemTintColor = Theme.textSecondary
        tabBar.isTranslucent = false
        
        let navBarAppearance = UINavigationBar.appearance()
        navBarAppearance.barTintColor = Theme.background
        navBarAppearance.tintColor = Theme.primary
        navBarAppearance.titleTextAttributes = [
            .foregroundColor: Theme.textPrimary,
            .font: UIFont.systemFont(ofSize: 18, weight: .bold)
        ]
        navBarAppearance.isTranslucent = false
    }
}
