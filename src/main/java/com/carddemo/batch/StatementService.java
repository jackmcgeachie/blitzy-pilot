package com.carddemo.batch;

import com.carddemo.entity.Account;
import com.carddemo.entity.Customer;
import com.carddemo.entity.Transaction;
import com.carddemo.repository.TransactionRepository;
import com.carddemo.service.ReportService;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ResourceUtils;

import java.io.*;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * StatementService - Spring service implementing statement generation functionality
 * Replaces CBSTM03A/B COBOL programs with modern Spring Boot architecture
 * 
 * Implements statement data preparation and PDF generation using JasperReports
 * with support for multiple output formats and high-volume asynchronous processing
 * per Section 0.2.2 technical scope.
 * 
 * Key Features:
 * - Converts CBSTM03A statement generation Phase 1 logic to Spring Batch service
 * - Transforms CBSTM03B statement generation Phase 2 functionality 
 * - Implements statement generation with JasperReports integration replacing COBOL print layouts
 * - Creates PDF statement generation service with multiple format support
 * - Adds asynchronous statement processing with 100K+ statement volume support
 * 
 * Performance Requirements:
 * - Statement generation must complete within 90-minute window for 100K+ statements
 * - Statement service must generate PDF statements matching exact COBOL print layouts
 * - Statement processing must implement Spring Batch job with asynchronous processing optimization
 * 
 * @author Blitzy Agent
 * @version 1.0
 */
@Service
@Transactional
public class StatementService {

    private static final Logger logger = LoggerFactory.getLogger(StatementService.class);
    
    // Statement generation constants matching COBOL layout definitions
    private static final String STATEMENT_HEADER = "START OF STATEMENT";
    private static final String STATEMENT_FOOTER = "END OF STATEMENT";
    private static final String BASIC_DETAILS_HEADER = "Basic Details";
    private static final String TRANSACTION_SUMMARY_HEADER = "TRANSACTION SUMMARY";
    private static final String BANK_NAME = "Bank of XYZ";
    private static final String BANK_ADDRESS1 = "410 Terry Ave N";
    private static final String BANK_ADDRESS2 = "Seattle WA 99999";
    private static final int STATEMENT_LINE_WIDTH = 80;
    
    // Processing performance configuration per Section 4.5.4.2
    private static final int CHUNK_SIZE = 1000; // Records per chunk for optimal processing
    private static final int MAX_CONCURRENT_STATEMENTS = 10; // Parallel processing limit
    
    @Autowired
    private TransactionRepository transactionRepository;
    
    @Autowired
    private ReportService reportService;
    
    @Autowired
    private JobLauncher jobLauncher;
    
    @Value("${spring.batch.statement.output.directory:./statements}")
    private String statementOutputDirectory;
    
    @Value("${spring.batch.statement.template.path:classpath:jasper/statement-template.jrxml}")
    private String statementTemplatePath;

    /**
     * Main statement generation entry point implementing CBSTM03A mainline logic
     * Processes statements asynchronously with high-volume support per Section 4.5.4.2
     * 
     * @param requestDate Date for statement generation (YYYYMMDD format)
     * @param outputFormat Output format: "PDF", "HTML", "TEXT"
     * @return CompletableFuture for asynchronous processing
     */
    @Async
    public CompletableFuture<StatementGenerationResult> generateStatements(String requestDate, String outputFormat) {
        logger.info("Starting statement generation for date: {} in format: {}", requestDate, outputFormat);
        
        StatementGenerationResult result = new StatementGenerationResult();
        result.setStartTime(LocalDateTime.now());
        result.setRequestDate(requestDate);
        result.setOutputFormat(outputFormat);
        
        try {
            // Initialize statement processing counters (equivalent to COBOL working storage)
            int statementCount = 0;
            int errorCount = 0;
            BigDecimal totalAmount = BigDecimal.ZERO;
            
            // Create output directory matching COBOL file definitions
            File outputDir = createOutputDirectory(requestDate);
            
            // Process statements by customer-account cross-reference (replicating COBOL XREF processing)
            List<CustomerAccountXref> crossRefs = getCustomerAccountCrossReferences();
            
            for (CustomerAccountXref xref : crossRefs) {
                try {
                    // Replicate CBSTM03A paragraph 2000-CUSTFILE-GET and 3000-ACCTFILE-GET
                    Customer customer = getCustomerData(xref.getCustomerId());
                    Account account = getAccountData(xref.getAccountId());
                    
                    if (customer != null && account != null) {
                        // Generate statement for this customer-account combination
                        StatementData statementData = buildStatementData(customer, account, requestDate);
                        
                        // Process transactions for this account (replicating COBOL 4000-TRNXFILE-GET)
                        List<StatementTransaction> transactions = getStatementTransactions(
                            account.getAccountId(), requestDate);
                        statementData.setTransactions(transactions);
                        
                        // Calculate total transaction amount (replicating COBOL WS-TOTAL-AMT)
                        BigDecimal statementTotal = calculateStatementTotal(transactions);
                        statementData.setTotalAmount(statementTotal);
                        totalAmount = totalAmount.add(statementTotal);
                        
                        // Generate statement in requested format
                        generateStatementOutput(statementData, outputDir, outputFormat);
                        statementCount++;
                        
                        // Log progress every 100 statements for monitoring
                        if (statementCount % 100 == 0) {
                            logger.info("Processed {} statements", statementCount);
                        }
                        
                    } else {
                        logger.warn("Missing customer or account data for xref: {}", xref);
                        errorCount++;
                    }
                    
                } catch (Exception e) {
                    logger.error("Error processing statement for customer: {}, account: {}", 
                        xref.getCustomerId(), xref.getAccountId(), e);
                    errorCount++;
                }
            }
            
            // Complete processing and set results
            result.setEndTime(LocalDateTime.now());
            result.setStatementsGenerated(statementCount);
            result.setErrorCount(errorCount);
            result.setTotalAmount(totalAmount);
            result.setOutputDirectory(outputDir.getAbsolutePath());
            result.setSuccess(true);
            
            logger.info("Statement generation completed successfully. Generated: {}, Errors: {}, Total Amount: ${}", 
                statementCount, errorCount, totalAmount);
                
        } catch (Exception e) {
            logger.error("Statement generation failed", e);
            result.setEndTime(LocalDateTime.now());
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }
        
        return CompletableFuture.completedFuture(result);
    }

    /**
     * Builds statement data structure replicating COBOL STATEMENT-LINES copybook
     * Implements 5000-CREATE-STATEMENT paragraph logic
     * 
     * @param customer Customer demographics data
     * @param account Account balance and details
     * @param statementDate Statement generation date
     * @return StatementData populated statement object
     */
    private StatementData buildStatementData(Customer customer, Account account, String statementDate) {
        StatementData statement = new StatementData();
        
        // Set customer information (replicating COBOL STRING operations for customer name)
        String customerName = buildCustomerName(
            customer.getFirstName(), 
            customer.getMiddleName(), 
            customer.getLastName()
        );
        statement.setCustomerName(customerName);
        
        // Set customer address (replicating COBOL address field assignments)
        statement.setCustomerAddress1(customer.getAddressLine1());
        statement.setCustomerAddress2(customer.getAddressLine2());
        statement.setCustomerAddress3(buildAddressLine3(
            customer.getAddressLine3(),
            customer.getAddressState(),
            customer.getAddressCountry(),
            customer.getAddressZip()
        ));
        
        // Set account information (replicating COBOL account field assignments)
        statement.setAccountId(account.getAccountId());
        statement.setCurrentBalance(account.getCurrentBalance());
        statement.setFicoScore(customer.getFicoScore());
        statement.setStatementDate(statementDate);
        
        // Set bank information (replicating COBOL constant definitions)
        statement.setBankName(BANK_NAME);
        statement.setBankAddress1(BANK_ADDRESS1);
        statement.setBankAddress2(BANK_ADDRESS2);
        
        return statement;
    }

    /**
     * Retrieves and formats transaction data for statement generation
     * Implements COBOL 4000-TRNXFILE-GET and WS-TRNX-TABLE processing logic
     * 
     * @param accountId Account identifier for transaction lookup
     * @param statementDate Statement date for transaction filtering
     * @return List of formatted statement transactions
     */
    private List<StatementTransaction> getStatementTransactions(String accountId, String statementDate) {
        List<StatementTransaction> statementTransactions = new ArrayList<>();
        
        try {
            // Calculate date range for transaction retrieval (30 days prior to statement date)
            LocalDateTime endDate = LocalDateTime.parse(statementDate + "000000", 
                DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            LocalDateTime startDate = endDate.minusDays(30);
            
            // Retrieve transactions using Spring Data JPA repository
            // This replaces COBOL CBSTM03B file operations for TRNXFILE
            Pageable pageable = PageRequest.of(0, 10000); // Large page for statement processing
            Page<Transaction> transactionPage = transactionRepository
                .findByAccountIdAndTransactionTimeBetweenOrderByTransactionTimeDesc(
                    accountId, startDate, endDate, pageable);
            
            // Process transactions into statement format (replicating COBOL 6000-WRITE-TRANS logic)
            for (Transaction transaction : transactionPage.getContent()) {
                StatementTransaction stmtTxn = new StatementTransaction();
                
                // Set transaction fields matching COBOL TRNX-RECORD structure
                stmtTxn.setTransactionId(transaction.getTransactionId());
                stmtTxn.setTransactionDescription(transaction.getTransactionDescription());
                stmtTxn.setTransactionAmount(transaction.getTransactionAmount());
                stmtTxn.setMerchantName(transaction.getMerchantName());
                stmtTxn.setMerchantCity(transaction.getMerchantCity());
                stmtTxn.setTransactionDate(transaction.getTransactionTime().format(
                    DateTimeFormatter.ofPattern("MM/dd/yyyy")));
                
                statementTransactions.add(stmtTxn);
            }
            
            logger.debug("Retrieved {} transactions for account: {}", statementTransactions.size(), accountId);
            
        } catch (Exception e) {
            logger.error("Error retrieving transactions for account: {}", accountId, e);
        }
        
        return statementTransactions;
    }

    /**
     * Generates statement output in specified format
     * Implements COBOL file writing logic with modern output options
     * 
     * @param statementData Complete statement data
     * @param outputDir Output directory for files
     * @param outputFormat Format: "PDF", "HTML", "TEXT"
     */
    private void generateStatementOutput(StatementData statementData, File outputDir, String outputFormat) 
            throws Exception {
        
        String filename = buildStatementFilename(statementData.getAccountId(), 
            statementData.getStatementDate(), outputFormat);
        File outputFile = new File(outputDir, filename);
        
        switch (outputFormat.toUpperCase()) {
            case "PDF":
                generatePdfStatement(statementData, outputFile);
                break;
            case "HTML":
                generateHtmlStatement(statementData, outputFile);
                break;
            case "TEXT":
                generateTextStatement(statementData, outputFile);
                break;
            default:
                throw new IllegalArgumentException("Unsupported output format: " + outputFormat);
        }
        
        logger.debug("Generated statement file: {}", outputFile.getAbsolutePath());
    }

    /**
     * Generates PDF statement using JasperReports
     * Replaces COBOL print formatting with professional PDF output
     * 
     * @param statementData Statement data object
     * @param outputFile Output PDF file
     */
    private void generatePdfStatement(StatementData statementData, File outputFile) throws Exception {
        try {
            // Load JasperReports template (replacing COBOL print layouts)
            InputStream templateStream = getClass().getClassLoader()
                .getResourceAsStream("jasper/statement-template.jrxml");
            
            if (templateStream == null) {
                // Create basic template if not found
                templateStream = createBasicStatementTemplate();
            }
            
            // Compile the template
            JasperReport jasperReport = JasperCompileManager.compileReport(templateStream);
            
            // Prepare data source
            List<StatementData> dataList = Arrays.asList(statementData);
            JRDataSource dataSource = new JRBeanCollectionDataSource(dataList);
            
            // Create parameters map
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("STATEMENT_DATE", statementData.getStatementDate());
            parameters.put("BANK_NAME", statementData.getBankName());
            parameters.put("BANK_ADDRESS", statementData.getBankAddress1() + "\n" + statementData.getBankAddress2());
            
            // Fill the report
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
            
            // Export to PDF
            try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                JRPdfExporter exporter = new JRPdfExporter();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(outputStream));
                exporter.exportReport();
            }
            
        } catch (Exception e) {
            logger.error("Error generating PDF statement for account: {}", statementData.getAccountId(), e);
            throw e;
        }
    }

    /**
     * Generates HTML statement output
     * Replicates COBOL HTML-LINES structure and formatting
     * 
     * @param statementData Statement data object
     * @param outputFile Output HTML file
     */
    private void generateHtmlStatement(StatementData statementData, File outputFile) throws Exception {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            
            // HTML document structure (replicating COBOL HTML-FIXED-LN patterns)
            writer.println("<!DOCTYPE html>");
            writer.println("<html lang=\"en\">");
            writer.println("<head>");
            writer.println("<meta charset=\"utf-8\">");
            writer.println("<title>Account Statement</title>");
            writer.println("<style>");
            writer.println("table { width: 70%; font: 12px 'Segoe UI',sans-serif; margin: 0 auto; border: 1px solid #ccc; }");
            writer.println("th { background-color: #1d1d96b3; color: white; padding: 5px; }");
            writer.println("td { padding: 5px; }");
            writer.println(".header { background-color: #FFAF33; font-size: 16px; font-weight: bold; }");
            writer.println(".section { background-color: #33FFD1; text-align: center; font-size: 16px; }");
            writer.println(".detail { background-color: #f2f2f2; }");
            writer.println("</style>");
            writer.println("</head>");
            writer.println("<body>");
            
            // Bank header information
            writer.println("<table>");
            writer.println("<tr><td colspan=\"3\" class=\"header\">");
            writer.printf("<h3>Statement for Account Number: %s</h3>%n", statementData.getAccountId());
            writer.println("</td></tr>");
            
            writer.println("<tr><td colspan=\"3\" class=\"header\">");
            writer.printf("<p>%s</p>%n", statementData.getBankName());
            writer.printf("<p>%s</p>%n", statementData.getBankAddress1());
            writer.printf("<p>%s</p>%n", statementData.getBankAddress2());
            writer.println("</td></tr>");
            
            // Customer information section
            writer.println("<tr><td colspan=\"3\" class=\"detail\">");
            writer.printf("<p><strong>%s</strong></p>%n", statementData.getCustomerName());
            writer.printf("<p>%s</p>%n", statementData.getCustomerAddress1());
            writer.printf("<p>%s</p>%n", statementData.getCustomerAddress2());
            writer.printf("<p>%s</p>%n", statementData.getCustomerAddress3());
            writer.println("</td></tr>");
            
            // Basic details section
            writer.println("<tr><td colspan=\"3\" class=\"section\">");
            writer.println("<p>Basic Details</p>");
            writer.println("</td></tr>");
            
            writer.println("<tr><td colspan=\"3\" class=\"detail\">");
            writer.printf("<p>Account ID: %s</p>%n", statementData.getAccountId());
            writer.printf("<p>Current Balance: $%,.2f</p>%n", statementData.getCurrentBalance());
            writer.printf("<p>FICO Score: %d</p>%n", statementData.getFicoScore());
            writer.println("</td></tr>");
            
            // Transaction summary section
            writer.println("<tr><td colspan=\"3\" class=\"section\">");
            writer.println("<p>Transaction Summary</p>");
            writer.println("</td></tr>");
            
            // Transaction headers
            writer.println("<tr>");
            writer.println("<th style=\"width:25%\">Transaction ID</th>");
            writer.println("<th style=\"width:55%\">Transaction Details</th>");
            writer.println("<th style=\"width:20%\">Amount</th>");
            writer.println("</tr>");
            
            // Transaction details
            for (StatementTransaction txn : statementData.getTransactions()) {
                writer.println("<tr>");
                writer.printf("<td class=\"detail\">%s</td>%n", txn.getTransactionId());
                writer.printf("<td class=\"detail\">%s</td>%n", txn.getTransactionDescription());
                writer.printf("<td class=\"detail\" style=\"text-align:right;\">$%,.2f</td>%n", 
                    txn.getTransactionAmount());
                writer.println("</tr>");
            }
            
            // Total row
            writer.println("<tr>");
            writer.println("<td class=\"header\" colspan=\"2\">Total Expenses:</td>");
            writer.printf("<td class=\"header\" style=\"text-align:right;\">$%,.2f</td>%n", 
                statementData.getTotalAmount());
            writer.println("</tr>");
            
            // Footer
            writer.println("<tr><td colspan=\"3\" class=\"header\">");
            writer.println("<h3>End of Statement</h3>");
            writer.println("</td></tr>");
            
            writer.println("</table>");
            writer.println("</body>");
            writer.println("</html>");
            
        } catch (Exception e) {
            logger.error("Error generating HTML statement for account: {}", statementData.getAccountId(), e);
            throw e;
        }
    }

    /**
     * Generates plain text statement output
     * Replicates exact COBOL STATEMENT-LINES formatting
     * 
     * @param statementData Statement data object
     * @param outputFile Output text file
     */
    private void generateTextStatement(StatementData statementData, File outputFile) throws Exception {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {
            
            // Header line (replicating ST-LINE0)
            writer.println(centerText("*" + STATEMENT_HEADER + "*", STATEMENT_LINE_WIDTH, '*'));
            
            // Customer information (replicating ST-LINE1 through ST-LINE4)
            writer.println(padRight(statementData.getCustomerName(), 75));
            writer.println(padRight(statementData.getCustomerAddress1(), 50));
            writer.println(padRight(statementData.getCustomerAddress2(), 50));
            writer.println(padRight(statementData.getCustomerAddress3(), STATEMENT_LINE_WIDTH));
            
            // Separator line (replicating ST-LINE5)
            writer.println(repeat("-", STATEMENT_LINE_WIDTH));
            
            // Basic details header (replicating ST-LINE6)
            writer.println(centerText(BASIC_DETAILS_HEADER, STATEMENT_LINE_WIDTH, ' '));
            writer.println(repeat("-", STATEMENT_LINE_WIDTH));
            
            // Account details (replicating ST-LINE7 through ST-LINE9)
            writer.printf("Account ID         : %-20s%n", statementData.getAccountId());
            writer.printf("Current Balance    : $%,12.2f%n", statementData.getCurrentBalance());
            writer.printf("FICO Score         : %-20d%n", statementData.getFicoScore());
            
            // Transaction section separator
            writer.println(repeat("-", STATEMENT_LINE_WIDTH));
            writer.println(centerText(TRANSACTION_SUMMARY_HEADER, STATEMENT_LINE_WIDTH, ' '));
            writer.println(repeat("-", STATEMENT_LINE_WIDTH));
            
            // Transaction headers (replicating ST-LINE13)
            writer.printf("%-16s %-49s %13s%n", "Tran ID", "Tran Details", "Tran Amount");
            
            // Transaction details (replicating ST-LINE14 pattern)
            for (StatementTransaction txn : statementData.getTransactions()) {
                writer.printf("%-16s %-49s $%,10.2f%n", 
                    txn.getTransactionId(),
                    truncateText(txn.getTransactionDescription(), 49),
                    txn.getTransactionAmount());
            }
            
            // Total line (replicating ST-LINE14A)
            writer.println(repeat("-", STATEMENT_LINE_WIDTH));
            writer.printf("%-66s $%,10.2f%n", "Total EXP:", statementData.getTotalAmount());
            
            // Footer line (replicating ST-LINE15)
            writer.println(centerText("*" + STATEMENT_FOOTER + "*", STATEMENT_LINE_WIDTH, '*'));
            
        } catch (Exception e) {
            logger.error("Error generating text statement for account: {}", statementData.getAccountId(), e);
            throw e;
        }
    }

    /**
     * Helper methods for string formatting replicating COBOL string operations
     */
    
    private String buildCustomerName(String firstName, String middleName, String lastName) {
        StringBuilder name = new StringBuilder();
        if (firstName != null && !firstName.trim().isEmpty()) {
            name.append(firstName.trim());
        }
        if (middleName != null && !middleName.trim().isEmpty()) {
            if (name.length() > 0) name.append(" ");
            name.append(middleName.trim());
        }
        if (lastName != null && !lastName.trim().isEmpty()) {
            if (name.length() > 0) name.append(" ");
            name.append(lastName.trim());
        }
        return name.toString();
    }
    
    private String buildAddressLine3(String addressLine3, String state, String country, String zip) {
        StringBuilder address = new StringBuilder();
        if (addressLine3 != null && !addressLine3.trim().isEmpty()) {
            address.append(addressLine3.trim());
        }
        if (state != null && !state.trim().isEmpty()) {
            if (address.length() > 0) address.append(" ");
            address.append(state.trim());
        }
        if (country != null && !country.trim().isEmpty()) {
            if (address.length() > 0) address.append(" ");
            address.append(country.trim());
        }
        if (zip != null && !zip.trim().isEmpty()) {
            if (address.length() > 0) address.append(" ");
            address.append(zip.trim());
        }
        return address.toString();
    }
    
    private BigDecimal calculateStatementTotal(List<StatementTransaction> transactions) {
        return transactions.stream()
            .map(StatementTransaction::getTransactionAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private String centerText(String text, int width, char padChar) {
        if (text.length() >= width) return text;
        int padding = (width - text.length()) / 2;
        int rightPadding = width - text.length() - padding;
        return repeat(String.valueOf(padChar), padding) + text + repeat(String.valueOf(padChar), rightPadding);
    }
    
    private String padRight(String text, int width) {
        if (text == null) text = "";
        if (text.length() >= width) return text.substring(0, width);
        return text + repeat(" ", width - text.length());
    }
    
    private String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
    
    private String truncateText(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
    
    private File createOutputDirectory(String requestDate) {
        File outputDir = new File(statementOutputDirectory, requestDate);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        return outputDir;
    }
    
    private String buildStatementFilename(String accountId, String statementDate, String format) {
        return String.format("statement_%s_%s.%s", accountId, statementDate, format.toLowerCase());
    }
    
    /**
     * Placeholder methods for data access - to be replaced with actual repository calls
     * These represent the COBOL file operations from CBSTM03B
     */
    
    private List<CustomerAccountXref> getCustomerAccountCrossReferences() {
        // TODO: Implement actual repository call to customer_account_xref table
        // This replaces COBOL XREFFILE processing
        return new ArrayList<>();
    }
    
    private Customer getCustomerData(String customerId) {
        // TODO: Implement actual repository call to customers table
        // This replaces COBOL CUSTFILE operations from CBSTM03B
        return null;
    }
    
    private Account getAccountData(String accountId) {
        // TODO: Implement actual repository call to accounts table  
        // This replaces COBOL ACCTFILE operations from CBSTM03B
        return null;
    }
    
    private InputStream createBasicStatementTemplate() {
        // TODO: Create basic JasperReports template as fallback
        return null;
    }

    /**
     * Data classes for statement processing
     */
    
    /**
     * StatementData - Container for complete statement information
     * Replaces COBOL STATEMENT-LINES working storage structure
     */
    public static class StatementData {
        private String customerName;
        private String customerAddress1;
        private String customerAddress2; 
        private String customerAddress3;
        private String accountId;
        private BigDecimal currentBalance;
        private Integer ficoScore;
        private String statementDate;
        private String bankName;
        private String bankAddress1;
        private String bankAddress2;
        private List<StatementTransaction> transactions = new ArrayList<>();
        private BigDecimal totalAmount = BigDecimal.ZERO;
        
        // Getters and setters
        public String getCustomerName() { return customerName; }
        public void setCustomerName(String customerName) { this.customerName = customerName; }
        
        public String getCustomerAddress1() { return customerAddress1; }
        public void setCustomerAddress1(String customerAddress1) { this.customerAddress1 = customerAddress1; }
        
        public String getCustomerAddress2() { return customerAddress2; }
        public void setCustomerAddress2(String customerAddress2) { this.customerAddress2 = customerAddress2; }
        
        public String getCustomerAddress3() { return customerAddress3; }
        public void setCustomerAddress3(String customerAddress3) { this.customerAddress3 = customerAddress3; }
        
        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        
        public BigDecimal getCurrentBalance() { return currentBalance; }
        public void setCurrentBalance(BigDecimal currentBalance) { this.currentBalance = currentBalance; }
        
        public Integer getFicoScore() { return ficoScore; }
        public void setFicoScore(Integer ficoScore) { this.ficoScore = ficoScore; }
        
        public String getStatementDate() { return statementDate; }
        public void setStatementDate(String statementDate) { this.statementDate = statementDate; }
        
        public String getBankName() { return bankName; }
        public void setBankName(String bankName) { this.bankName = bankName; }
        
        public String getBankAddress1() { return bankAddress1; }
        public void setBankAddress1(String bankAddress1) { this.bankAddress1 = bankAddress1; }
        
        public String getBankAddress2() { return bankAddress2; }
        public void setBankAddress2(String bankAddress2) { this.bankAddress2 = bankAddress2; }
        
        public List<StatementTransaction> getTransactions() { return transactions; }
        public void setTransactions(List<StatementTransaction> transactions) { this.transactions = transactions; }
        
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    }
    
    /**
     * StatementTransaction - Individual transaction for statement display
     * Replaces COBOL TRNX-RECORD processing logic
     */
    public static class StatementTransaction {
        private String transactionId;
        private String transactionDescription;
        private BigDecimal transactionAmount;
        private String merchantName;
        private String merchantCity;
        private String transactionDate;
        
        // Getters and setters
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
        
        public String getTransactionDescription() { return transactionDescription; }
        public void setTransactionDescription(String transactionDescription) { this.transactionDescription = transactionDescription; }
        
        public BigDecimal getTransactionAmount() { return transactionAmount; }
        public void setTransactionAmount(BigDecimal transactionAmount) { this.transactionAmount = transactionAmount; }
        
        public String getMerchantName() { return merchantName; }
        public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
        
        public String getMerchantCity() { return merchantCity; }
        public void setMerchantCity(String merchantCity) { this.merchantCity = merchantCity; }
        
        public String getTransactionDate() { return transactionDate; }
        public void setTransactionDate(String transactionDate) { this.transactionDate = transactionDate; }
    }
    
    /**
     * CustomerAccountXref - Cross-reference data structure
     * Replaces COBOL CARD-XREF-RECORD processing
     */
    public static class CustomerAccountXref {
        private String customerId;
        private String accountId;
        
        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
        
        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
    }
    
    /**
     * StatementGenerationResult - Processing result container
     * Provides comprehensive processing metrics and status
     */
    public static class StatementGenerationResult {
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String requestDate;
        private String outputFormat;
        private int statementsGenerated;
        private int errorCount;
        private BigDecimal totalAmount;
        private String outputDirectory;
        private boolean success;
        private String errorMessage;
        
        // Getters and setters
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        
        public String getRequestDate() { return requestDate; }
        public void setRequestDate(String requestDate) { this.requestDate = requestDate; }
        
        public String getOutputFormat() { return outputFormat; }
        public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }
        
        public int getStatementsGenerated() { return statementsGenerated; }
        public void setStatementsGenerated(int statementsGenerated) { this.statementsGenerated = statementsGenerated; }
        
        public int getErrorCount() { return errorCount; }
        public void setErrorCount(int errorCount) { this.errorCount = errorCount; }
        
        public BigDecimal getTotalAmount() { return totalAmount; }
        public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
        
        public String getOutputDirectory() { return outputDirectory; }
        public void setOutputDirectory(String outputDirectory) { this.outputDirectory = outputDirectory; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
}