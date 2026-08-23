import UIKit
import PDFKit

class PdfViewerViewController: UIViewController, UIGestureRecognizerDelegate {

    private let fileUrl: URL
    private let docTitle: String
    private var pdfView = PDFView()
    private var pageLabel = UILabel()
    private var isHeaderHidden = false
    
    init(fileUrl: URL, title: String) {
        self.fileUrl = fileUrl
        self.docTitle = title
        super.init(nibName: nil, bundle: nil)
    }
    
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = Theme.background
        title = docTitle
        
        setupPdfView()
        setupTopBarActions()
        setupPageIndicator()
        setupTapGesture()
        loadDocument()
    }
    
    private func setupPdfView() {
        pdfView.translatesAutoresizingMaskIntoConstraints = false
        pdfView.autoScales = true
        pdfView.displayMode = .singlePageContinuous
        pdfView.displayDirection = .vertical
        pdfView.backgroundColor = Theme.background
        pdfView.minScaleFactor = 1.0
        pdfView.maxScaleFactor = 4.0
        view.addSubview(pdfView)
        
        NSLayoutConstraint.activate([
            pdfView.topAnchor.constraint(equalTo: view.topAnchor),
            pdfView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            pdfView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            pdfView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        
        NotificationCenter.default.addObserver(self, selector: #selector(pageChanged), name: .PDFViewPageChanged, object: nil)
    }
    
    private func setupTopBarActions() {
        let shareBtn = UIBarButtonItem(barButtonSystemItem: .action, target: self, action: #selector(shareDocument))
        let fitBtn = UIBarButtonItem(title: "Fit", style: .plain, target: self, action: #selector(resetZoom))
        navigationItem.rightBarButtonItems = [shareBtn, fitBtn]
    }
    
    private func setupPageIndicator() {
        pageLabel.backgroundColor = UIColor(white: 0, alpha: 0.8)
        pageLabel.textColor = .white
        pageLabel.font = UIFont.systemFont(ofSize: 12, weight: .bold)
        pageLabel.textAlignment = .center
        pageLabel.layer.cornerRadius = 14
        pageLabel.layer.masksToBounds = true
        pageLabel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(pageLabel)
        
        NSLayoutConstraint.activate([
            pageLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            pageLabel.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -16),
            pageLabel.heightAnchor.constraint(equalToConstant: 28),
            pageLabel.widthAnchor.constraint(greaterThanOrEqualToConstant: 80)
        ])
    }
    
    private func setupTapGesture() {
        let singleTap = UITapGestureRecognizer(target: self, action: #selector(toggleImmersiveMode))
        singleTap.numberOfTapsRequired = 1
        singleTap.delegate = self
        pdfView.addGestureRecognizer(singleTap)
    }
    
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        return true
    }
    
    @objc private func toggleImmersiveMode() {
        isHeaderHidden.toggle()
        navigationController?.setNavigationBarHidden(isHeaderHidden, animated: true)
        UIView.animate(withDuration: 0.25) {
            self.pageLabel.alpha = self.isHeaderHidden ? 0.0 : 1.0
        }
    }
    
    private func loadDocument() {
        guard let doc = PDFDocument(url: fileUrl) else { return }
        pdfView.document = doc
        updatePageLabel()
    }
    
    @objc private func pageChanged() {
        updatePageLabel()
    }
    
    private func updatePageLabel() {
        guard let doc = pdfView.document, let currentPage = pdfView.currentPage else { return }
        let index = doc.index(for: currentPage) + 1
        pageLabel.text = "  \(index) / \(doc.pageCount)  "
    }
    
    @objc private func resetZoom() {
        pdfView.scaleFactor = pdfView.minScaleFactor
    }
    
    @objc private func shareDocument() {
        let activityVC = UIActivityViewController(activityItems: [fileUrl], applicationActivities: nil)
        if let popover = activityVC.popoverPresentationController {
            popover.barButtonItem = navigationItem.rightBarButtonItems?.first
        }
        present(activityVC, animated: true)
    }
}
