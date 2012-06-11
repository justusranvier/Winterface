package freenet.winterface.web.markup;

import org.apache.wicket.Component;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.PropertyListView;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;

import freenet.clients.http.bookmark.BookmarkCategory;
import freenet.winterface.web.core.BookmarkItemView;

/**
 * A {@link DashboardPanel} which recursively renders bookmark categories.
 * 
 * @author pouyan
 * @see BookmarkItemView
 */
@SuppressWarnings("serial")
public class BookmarkCategoryPanel extends Panel {

	/**
	 * Model of {@link BookmarkCategory} for this panel
	 */
	private IModel<BookmarkCategory> model;

	/**
	 * Constructs
	 * @param id id of HTML tag to replace this panel with
	 * @param model data model of this panel
	 */
	public BookmarkCategoryPanel(String id, IModel<BookmarkCategory> model) {
		super(id, model);
		this.model = model;
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();
		// Add category name and BookmarkItem(s)
		add(new Label("name"));
		add(new BookmarkItemView("items"));
		BookmarkCategory category = model.getObject();
		// Check for sub categories
		Component subCats = null;
		if (category.getAllSubCategories().size() == 0) {
			subCats = new WebMarkupContainer("allSubCategories");
			subCats.setVisible(false);
		} else {
			subCats = new PropertyListView<BookmarkCategory>("allSubCategories") {

				@Override
				protected void populateItem(ListItem<BookmarkCategory> item) {
					item.add(new BookmarkCategoryPanel("content", item.getModel()));
				}

			};

		}
		add(subCats);
	}

}