package org.example.controller;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;
import org.example.model.entity.Category;
import org.example.model.entity.Transaction;
import org.example.model.enums.CategoryType;
import org.example.model.enums.TransactionType;
import org.example.repository.CategoryRepository;
import org.example.repository.TransactionFilter;
import org.example.repository.TransactionRepository;
import org.example.service.CategoryService;
import org.example.service.TransactionService;
import org.example.util.CurrencyFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class TransactionController {

    private static final Logger log = LoggerFactory.getLogger(TransactionController.class);
    private static final int PAGE_SIZE = 20;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    // --- TableView ---
    @FXML private TableView<Transaction>           tableView;
    @FXML private TableColumn<Transaction, LocalDate>       colDate;
    @FXML private TableColumn<Transaction, TransactionType> colType;
    @FXML private TableColumn<Transaction, String>          colCategory;
    @FXML private TableColumn<Transaction, BigDecimal>      colAmount;
    @FXML private TableColumn<Transaction, String>          colComment;
    @FXML private TableColumn<Transaction, Void>            colActions;

    // --- Фильтры ---
    @FXML private ComboBox<TransactionType> filterType;
    @FXML private ComboBox<Category>        filterCategory;
    @FXML private DatePicker                filterDateFrom;
    @FXML private DatePicker                filterDateTo;
    @FXML private TextField                 filterSearch;

    // --- Пагинация ---
    @FXML private Button btnPrev;
    @FXML private Button btnNext;
    @FXML private Label  pageLabel;

    private TransactionService service;
    private CategoryService    categoryService;
    private int currentPage = 0;

    @FXML
    public void initialize() {
        service         = new TransactionService(new TransactionRepository());
        categoryService = new CategoryService(new CategoryRepository());

        setupColumns();
        setupFilters();
        refresh();
    }

    // ------------------------------------------------------------------ columns

    private void setupColumns() {
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colDate.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(LocalDate d, boolean empty) {
                super.updateItem(d, empty);
                if (empty || d == null) { setText(null); setStyle(""); return; }
                setText(d.format(DATE_FMT));
                setStyle("-fx-font-family: 'JetBrains Mono'; -fx-font-size: 12px;");
            }
        });

        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colType.setCellFactory(col -> new TableCell<>() {
            private final javafx.scene.control.Label badge = new javafx.scene.control.Label();
            @Override protected void updateItem(TransactionType t, boolean empty) {
                super.updateItem(t, empty);
                if (empty || t == null) { setGraphic(null); return; }
                badge.setText(t == TransactionType.INCOME ? "Доход" : "Расход");
                badge.getStyleClass().setAll(
                        t == TransactionType.INCOME ? "tx-badge-income" : "tx-badge-expense");
                setGraphic(badge);
                setText(null);
            }
        });

        colCategory.setCellValueFactory(cell -> {
            Category cat = cell.getValue().getCategory();
            return new javafx.beans.property.SimpleStringProperty(cat != null ? cat.getName() : "—");
        });

        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colAmount.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal amount, boolean empty) {
                super.updateItem(amount, empty);
                if (empty || amount == null) { setText(null); setStyle(""); return; }
                setText(CurrencyFormatter.format(amount));
                setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
                int idx = getIndex();
                if (idx >= 0 && idx < getTableView().getItems().size()) {
                    TransactionType t = getTableView().getItems().get(idx).getType();
                    setStyle(t == TransactionType.INCOME
                            ? "-fx-text-fill: #10B981; -fx-font-family: 'JetBrains Mono'; -fx-font-weight: bold;"
                            : "-fx-text-fill: #EF4444; -fx-font-family: 'JetBrains Mono'; -fx-font-weight: bold;");
                }
            }
        });

        colComment.setCellValueFactory(new PropertyValueFactory<>("comment"));
        colComment.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String comment, boolean empty) {
                super.updateItem(comment, empty);
                if (empty || comment == null || comment.isBlank()) {
                    setText(null); setStyle(""); return;
                }
                setText(comment);
                setStyle("-fx-text-fill: #6B7280;");
            }
        });

        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button del = new Button("✕");
            {
                del.getStyleClass().add("btn-icon-danger");
                del.setOnAction(e -> {
                    Transaction t = getTableView().getItems().get(getIndex());
                    if (t != null) confirmDelete(t);
                });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : del);
            }
        });

        tableView.setRowFactory(tv -> {
            TableRow<Transaction> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) showDialog(row.getItem());
            });
            return row;
        });
    }

    // ------------------------------------------------------------------ filters

    private void setupFilters() {
        filterType.getItems().add(null);
        filterType.getItems().addAll(TransactionType.values());
        filterType.setConverter(new StringConverter<>() {
            @Override public String toString(TransactionType t) {
                if (t == null) return "Все типы";
                return t == TransactionType.INCOME ? "Доходы" : "Расходы";
            }
            @Override public TransactionType fromString(String s) { return null; }
        });
        filterType.valueProperty().addListener((obs, old, val) -> {
            currentPage = 0;
            refreshCategoryFilter(val);
        });

        filterCategory.setConverter(new StringConverter<>() {
            @Override public String toString(Category c) { return c == null ? "Все категории" : c.getName(); }
            @Override public Category fromString(String s) { return null; }
        });
        filterCategory.valueProperty().addListener((obs, old, val) -> resetPage());

        filterDateFrom.valueProperty().addListener((obs, old, val) -> resetPage());
        filterDateTo.valueProperty().addListener((obs, old, val)   -> resetPage());
        filterSearch.textProperty().addListener((obs, old, val)    -> resetPage());

        refreshCategoryFilter(null);
    }

    private void refreshCategoryFilter(TransactionType type) {
        Category prev = filterCategory.getValue();
        filterCategory.getItems().clear();
        filterCategory.getItems().add(null);

        List<Category> cats;
        if (type == TransactionType.INCOME)       cats = categoryService.getByType(CategoryType.INCOME);
        else if (type == TransactionType.EXPENSE) cats = categoryService.getByType(CategoryType.EXPENSE);
        else                                      cats = categoryService.getAll();

        filterCategory.getItems().addAll(cats);
        if (cats.contains(prev)) filterCategory.setValue(prev);
    }

    // ------------------------------------------------------------------ pagination

    @FXML private void prevPage() { currentPage--; refresh(); }
    @FXML private void nextPage() { currentPage++; refresh(); }

    @FXML private void resetFilters() {
        filterType.setValue(null);
        filterCategory.setValue(null);
        filterDateFrom.setValue(null);
        filterDateTo.setValue(null);
        filterSearch.clear();
        resetPage();
    }

    private void resetPage() { currentPage = 0; refresh(); }

    private void refresh() {
        TransactionFilter filter = buildFilter();
        List<Transaction> page   = service.getPage(filter, currentPage, PAGE_SIZE);
        long total               = service.count(filter);
        int totalPages           = (int) Math.max(1, Math.ceil((double) total / PAGE_SIZE));

        tableView.getItems().setAll(page);
        pageLabel.setText("Страница " + (currentPage + 1) + " из " + totalPages);
        btnPrev.setDisable(currentPage == 0);
        btnNext.setDisable(currentPage >= totalPages - 1);
    }

    private TransactionFilter buildFilter() {
        Long catId = filterCategory.getValue() != null ? filterCategory.getValue().getId() : null;
        return new TransactionFilter(
                filterType.getValue(), catId,
                filterDateFrom.getValue(), filterDateTo.getValue(),
                filterSearch.getText());
    }

    // ------------------------------------------------------------------ add/edit dialog

    @FXML private void openAddDialog() { showDialog(null); }

    private void showDialog(Transaction existing) {
        boolean isEdit = existing != null;

        Dialog<Transaction> dialog = new Dialog<>();
        dialog.setTitle(isEdit ? "Редактировать транзакцию" : "Новая транзакция");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().getStylesheets().addAll(tableView.getScene().getStylesheets());

        // --- Form ---
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(14);
        grid.setPadding(new Insets(20, 24, 8, 24));

        ComboBox<TransactionType> typeBox = new ComboBox<>();
        typeBox.getItems().addAll(TransactionType.values());
        typeBox.setConverter(new StringConverter<>() {
            @Override public String toString(TransactionType t) {
                return t == null ? "" : (t == TransactionType.INCOME ? "Доход" : "Расход");
            }
            @Override public TransactionType fromString(String s) { return null; }
        });
        typeBox.setValue(isEdit ? existing.getType() : TransactionType.EXPENSE);
        typeBox.setPrefWidth(220);

        ComboBox<Category> categoryBox = new ComboBox<>();
        categoryBox.setConverter(new StringConverter<>() {
            @Override public String toString(Category c) { return c == null ? "" : c.getName(); }
            @Override public Category fromString(String s) { return null; }
        });
        categoryBox.setPrefWidth(220);

        Runnable loadCategories = () -> {
            TransactionType t  = typeBox.getValue();
            CategoryType    ct = t == TransactionType.INCOME ? CategoryType.INCOME : CategoryType.EXPENSE;
            List<Category>  cats = categoryService.getByType(ct);
            categoryBox.getItems().setAll(cats);
            if (isEdit && existing.getCategory() != null) {
                cats.stream()
                    .filter(c -> c.getId().equals(existing.getCategory().getId()))
                    .findFirst()
                    .ifPresent(categoryBox::setValue);
            } else if (!cats.isEmpty()) {
                categoryBox.setValue(cats.get(0));
            }
        };
        typeBox.valueProperty().addListener((obs, old, val) -> loadCategories.run());
        loadCategories.run();

        TextField  amountField  = new TextField(isEdit ? existing.getAmount().toPlainString() : "");
        amountField.setPromptText("0.00");
        amountField.setPrefWidth(220);

        DatePicker datePicker   = new DatePicker(isEdit ? existing.getDate() : LocalDate.now());
        datePicker.setPrefWidth(220);

        TextField  commentField = new TextField(isEdit && existing.getComment() != null ? existing.getComment() : "");
        commentField.setPromptText("Необязательно");
        commentField.setPrefWidth(220);

        grid.add(new Label("Тип:"),          0, 0); grid.add(typeBox,      1, 0);
        grid.add(new Label("Категория:"),    0, 1); grid.add(categoryBox,  1, 1);
        grid.add(new Label("Сумма, ₽:"),    0, 2); grid.add(amountField,  1, 2);
        grid.add(new Label("Дата:"),         0, 3); grid.add(datePicker,   1, 3);
        grid.add(new Label("Комментарий:"), 0, 4); grid.add(commentField, 1, 4);

        dialog.getDialogPane().setContent(grid);

        // Валидация
        Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);
        Runnable validate = () -> {
            boolean valid;
            try {
                BigDecimal a = new BigDecimal(amountField.getText().replace(",", ".").trim());
                valid = a.compareTo(BigDecimal.ZERO) > 0
                        && categoryBox.getValue() != null
                        && datePicker.getValue() != null;
            } catch (NumberFormatException e) {
                valid = false;
            }
            okBtn.setDisable(!valid);
        };
        amountField.textProperty().addListener((obs, o, n)  -> validate.run());
        categoryBox.valueProperty().addListener((obs, o, n) -> validate.run());
        datePicker.valueProperty().addListener((obs, o, n)  -> validate.run());
        validate.run();

        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;
            try {
                BigDecimal amount = new BigDecimal(amountField.getText().replace(",", ".").trim());
                Transaction t = isEdit ? existing : new Transaction();
                t.setType(typeBox.getValue());
                t.setCategory(categoryBox.getValue());
                t.setAmount(amount);
                t.setDate(datePicker.getValue());
                t.setComment(commentField.getText().isBlank() ? null : commentField.getText().trim());
                return t;
            } catch (NumberFormatException e) {
                log.warn("Некорректная сумма: {}", amountField.getText());
                return null;
            }
        });

        dialog.showAndWait().ifPresent(t -> { service.save(t); refresh(); });
    }

    // ------------------------------------------------------------------ delete

    private void confirmDelete(Transaction t) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Удаление");
        confirm.setHeaderText("Удалить транзакцию?");
        confirm.setContentText(String.format("%s  %s  %s",
                t.getDate().format(DATE_FMT),
                CurrencyFormatter.format(t.getAmount()),
                t.getCategory() != null ? t.getCategory().getName() : ""));
        confirm.getDialogPane().getStylesheets().addAll(tableView.getScene().getStylesheets());
        confirm.showAndWait()
               .filter(b -> b == ButtonType.OK)
               .ifPresent(b -> { service.delete(t); refresh(); });
    }
}
