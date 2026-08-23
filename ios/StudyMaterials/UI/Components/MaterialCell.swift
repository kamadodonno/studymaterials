import UIKit

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
        let modStr = material.moduleName.isEmpty ? "General Practice" : material.moduleName
        metaLabel.text = "\(material.fileType.uppercased()) • \(modStr)"
        badgeLabel.text = isDownloaded ? "✓ Available Offline" : "☁️ Tap to download"
        badgeLabel.textColor = isDownloaded ? Theme.secondary : Theme.primary
    }
}
