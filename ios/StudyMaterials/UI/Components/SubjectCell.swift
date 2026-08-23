import UIKit

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
