package com.rtcsoft.sevakendra.services;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.rtcsoft.sevakendra.dtos.DocxTemplateRequestBodyDTO;
import com.rtcsoft.sevakendra.dtos.DocxTemplateRequestBodyDTO.DocTemplate;
import com.rtcsoft.sevakendra.entities.Customer;
import com.rtcsoft.sevakendra.entities.CustomerDocument;
import com.rtcsoft.sevakendra.exceptions.ApiException;
import com.rtcsoft.sevakendra.repositories.CustomerDocumentRepository;
import com.rtcsoft.sevakendra.repositories.CustomerRepository;

import jakarta.servlet.http.HttpServletRequest;

/**
 * @class WordService
 * @author Ushaikh
 **/

@Service
public class DocxTemplateService {

	private static String IMG_SRC = "src/main/resources/images/rtc-logo.png";

	private static String SRC = "src/main/resources/docs/cast-certificate.docx";
	private static String DOC_TEMPLATE_SRC_PATH = "src/main/resources/docs/";
	private static String GEN_DOCS_DEST_PATH = "src/main/resources/static/generated/docs/";
	// private static String FONT1 =
	// "src/main/resources/fonts/NotoSans/static/NotoSans-Black.ttf";
	private static String FONT2 = "src/main/resources/fonts/freesans/FreeSans.ttf";

	private static final Logger LOGGER = LoggerFactory.getLogger(DocxTemplateService.class);

	@Autowired
	CustomerRepository customerRepository;

	@Autowired
	CustomerDocumentRepository customerDocumentRepository;

	@Autowired
	private SharedService sharedService;

	@Autowired
	HttpServletRequest request;

	// Create a list to hold file paths
	List<String> fileList = new ArrayList<>();

	public DocxTemplateService() {
		super();
		createDestinationFolder(GEN_DOCS_DEST_PATH);

		// Scan the directory and populate the list
		scanDirectory(new File(DOC_TEMPLATE_SRC_PATH), fileList);

		LOGGER.info("SCANNED_DOCS_TEMPLATE_FILES_LIST");
		System.out.println(fileList);
	}

	private void createDestinationFolder(String folderPath) {
		// Create a File object
		File folder = new File(folderPath);

		// Check if the folder exists
		if (!folder.exists()) {
			// Create the directories
			boolean created = folder.mkdirs();

			// Verify if the directories were successfully created
			if (created) {
				System.out.println("Folders created successfully: " + folderPath);
			} else {
				System.out.println("Failed to create folders: " + folderPath);
			}
		}
	}

	/**
	 * Recursively scans a directory and adds file paths to the list.
	 *
	 * @param folder   The directory to scan
	 * @param fileList The list to store file paths
	 */
	public static void scanDirectory(File folder, List<String> fileList) {
		if (folder.exists() && folder.isDirectory()) {
			File[] files = folder.listFiles();
			if (files != null) {
				for (File file : files) {
					if (file.isFile()) {
						// Add file name to the list
						fileList.add(file.getName());
					} else if (file.isDirectory()) {
						// Recurse into sub directory
						scanDirectory(file, fileList);
					}
				}
			}
		} else {
			System.out.println("The folder does not exist or is not a directory: " + folder.getAbsolutePath());
		}
	}

	/**
	 * Create or Update help to generate document with new/updated input
	 * 
	 * @param input
	 * @return CustomerDocument
	 * @throws ApiException
	 */
	public ResponseEntity<List<DocTemplate>> createOrUpdate(DocxTemplateRequestBodyDTO input) throws ApiException {
//		System.out.println("IDs: " + input.getIds());

		List<Integer> customerIds = input.getCustomerIds();
		List<DocTemplate> docTemplates = input.getDocTemplates();
		System.out.println("Customer IDs: " + customerIds);

		try {
			customerIds.forEach(customerId -> {
				docTemplates.forEach(template -> {
					System.out.println("Template ID: " + template.getId());
					System.out.println("Title: " + template.getTitle());
					System.out.println("Active: " + template.isActive());
					System.out.println("Checked: " + template.isChecked());
					System.out.println("customerId: " + customerId);

					boolean docTemplateExists = fileList.contains(template.getTitle());

					if (docTemplateExists && template.isActive()) {
						try {
							String filePath = generateDocument(template, customerId);
							System.out.println("Printing After Generating Docs");
							System.out.println(filePath);
							Path path = Paths.get(filePath);
							String fileName = path.getFileName().toString();
							if (fileName != null) {
								CustomerDocument custDoc = new CustomerDocument(); // existingCustDoc.orElseGet(CustomerDocument::new);
								custDoc.setCustomerId(customerId); // .orElseGet(custDoc.getCustomerId()));
								custDoc.setDocName(fileName); // .orElse(custDoc.getDocName()));
								custDoc.setThumbnail(null); // .orElse(custDoc.getThumbnail()));
								custDoc.setDocPath(filePath);

								long authUserId = sharedService.getUserIdFromHeader(request);
								custDoc.setUserId(authUserId);

								customerDocumentRepository.save(custDoc);
							}
						} catch (IOException e) {
							e.printStackTrace();
							return;
						}
					}

				});
			});

			return ResponseEntity.status(HttpStatus.CREATED).body(docTemplates);
		} catch (Exception e2) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
		}

//		try {
//			Optional<CustomerDocument> existingCustDoc = customerDocumentRepository.findById(input.getId());
//			String path = generateDocument(input, existingCustDoc);
//			if (path != null) {
//				CustomerDocument custDoc = existingCustDoc.orElseGet(CustomerDocument::new);
//				custDoc.setCustomerId(Optional.ofNullable(input.getCustomerId()).orElse(custDoc.getCustomerId()));
//				custDoc.setDocName(Optional.ofNullable(input.getDocName()).orElse(custDoc.getDocName()));
//				custDoc.setThumbnail(Optional.ofNullable(input.getThumbnail()).orElse(custDoc.getThumbnail()));
//				custDoc.setDocPath(path);
//				
//				long authUserId = sharedService.getUserIdFromHeader(request);
//				custDoc.setUserId(authUserId);
//
//				customerDocumentRepository.save(custDoc);
//
//				return ResponseEntity.status(HttpStatus.CREATED).body(custDoc);
//			}
//
//		} catch (Exception e) {
//			e.printStackTrace();
//			LOGGER.error(e.getMessage());
//		}
//		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
	}

	public ResponseEntity<List<CustomerDocument>> getAllDocuments() {
		Long userId = sharedService.getUserIdFromSession(request);
		List<CustomerDocument> customerDocuments = customerDocumentRepository.findAllByUserId(userId);
		return ResponseEntity.status(HttpStatus.OK).body(customerDocuments);
	}

	public ResponseEntity<Optional<CustomerDocument>> findById(long id) {
		Long userId = sharedService.getUserIdFromSession(request);
		Optional<CustomerDocument> cdoc = customerDocumentRepository.findWithUserId(id, userId);
		if (cdoc.isEmpty()) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
		}

		return ResponseEntity.status(HttpStatus.OK).body(cdoc);
	}

	public ResponseEntity<CustomerDocument> delete(long id) throws ApiException {
		CustomerDocument existingCustDoc = customerDocumentRepository.findById(id)
				.orElseThrow(() -> new RuntimeException("Customer not found with id " + id));
		customerDocumentRepository.deleteById(id);
		return ResponseEntity.status(HttpStatus.OK).body(existingCustDoc);
	}

	private Optional<HashMap<String, Object>> prepareDataMap(Optional<Customer> customer) {
		try {
			Calendar calendar = Calendar.getInstance();
			SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
			String formattedDate = formatter.format(calendar.getTime());

			Optional<HashMap<String, Object>> placeholders = customer.map(cust -> {
				HashMap<String, Object> map = new HashMap<>();
				map.put("firstName", cust.getFirstName());
				map.put("middleName", cust.getMiddleName());
				map.put("lastName", cust.getLastName());
				map.put("phoneNumber", cust.getPhoneNumber());
				map.put("address", cust.getAddress());
				map.put("place", cust.getPlace());
				map.put("age", cust.getAge());
				map.put("cast", cust.getCast());
				map.put("occupation", cust.getOccupation());
				map.put("aadharNumber", cust.getAadharNumber());
				map.put("image", cust.getImage());
				map.put("date", formattedDate);
				return map;
			});

			return placeholders;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Method for generating DocX report by replacing data in existing template
	 * 
	 * @param existingCustDoc
	 *
	 * @param docXTemplateFileWithExtension given name of docX template with
	 *                                      extension
	 * @param data                          given map of data parameters that need
	 *                                      to be replacement for docX placeholders
	 * @return
	 * @return generated report name
	 * @throws IOException input|output exception
	 **/

	public String generateDocument(DocTemplate template, int customerId) throws IOException {
		Optional<Customer> customer = customerRepository.findById(customerId);
		Supplier<RuntimeException> exceptionSupplier = () -> new RuntimeException("Customer not found");
		String firstName = (customer.map(Customer::getFirstName).orElseThrow(exceptionSupplier)).toLowerCase();
		String lastName = (customer.map(Customer::getLastName).orElseThrow(exceptionSupplier)).toLowerCase();

		Optional<HashMap<String, Object>> data = prepareDataMap(customer);

		if (!data.isEmpty()) {
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			String docTemplatePath = DOC_TEMPLATE_SRC_PATH + template.getTitle();
			FileInputStream fis = new FileInputStream(docTemplatePath);
			try (XWPFDocument xwpfDocument = new XWPFDocument(fis)) {
				replacePlaceholdersInParagraphs(data, xwpfDocument);
				replacePlaceholderInTables(data, xwpfDocument);

				xwpfDocument.write(outputStream);

				// Create target path using first name, last name and document name from this
//				String docNameFromDb = existingCustDoc.map(CustomerDocument::getDocName).orElse("unknown-document");
				String docName = template.getTitle();
				String targetFilePath = String.format("%s%s-%s-%s", GEN_DOCS_DEST_PATH, firstName, lastName, docName);

				Path path = Paths.get(targetFilePath);
				System.out.println("File created at " + path);
				Files.write(path, outputStream.toByteArray());
				return path.toString();
			} catch (Exception e) {
				LOGGER.error("Error occurred while generating report: {}", e.getMessage());
			}
		}
		return null;
	}

//	private Optional<HashMap<String, Object>> prepareDataMap(CustomerDocumentDTO customerDocument) {
//		try {
//			Optional<Customer> customer = customerRepository.findById(customerDocument.getCustomerId());
//
//			Calendar calendar = Calendar.getInstance();
//			SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy");
//			String formattedDate = formatter.format(calendar.getTime());
//
//			Optional<HashMap<String, Object>> placeholders = customer.map(cust -> {
//				HashMap<String, Object> map = new HashMap<>();
//				map.put("firstName", cust.getFirstName());
//				map.put("middleName", cust.getMiddleName());
//				map.put("lastName", cust.getLastName());
//				map.put("phoneNumber", cust.getPhoneNumber());
//				map.put("address", cust.getAddress());
//				map.put("place", cust.getPlace());
//				map.put("age", cust.getAge());
//				map.put("cast", cust.getCast());
//				map.put("occupation", cust.getOccupation());
//				map.put("aadharNumber", cust.getAadharNumber());
//				map.put("image", cust.getImage());
//				map.put("date", formattedDate);
//				return map;
//			});
//
//			return placeholders;
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		return null;
//	}
//
//	/**
//	 * Method for generating DocX report by replacing data in existing template
//	 * 
//	 * @param existingCustDoc
//	 *
//	 * @param docXTemplateFileWithExtension given name of docX template with
//	 *                                      extension
//	 * @param data                          given map of data parameters that need
//	 *                                      to be replacement for docX placeholders
//	 * @return
//	 * @return generated report name
//	 * @throws IOException input|output exception
//	 **/
//
//	public String generateDocument(CustomerDocumentDTO customerDocument, Optional<CustomerDocument> existingCustDoc)
//			throws IOException {
//		Optional<HashMap<String, Object>> data = prepareDataMap(customerDocument);
//		if (!data.isEmpty()) {
//			Optional<Customer> customer = customerRepository.findById(customerDocument.getCustomerId());
//
//			Supplier<RuntimeException> exceptionSupplier = () -> new RuntimeException("Customer not found");
//			String firstName = (customer.map(Customer::getFirstName).orElseThrow(exceptionSupplier)).toLowerCase();
//			String lastName = (customer.map(Customer::getLastName).orElseThrow(exceptionSupplier)).toLowerCase();
//
//			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
//			FileInputStream fis = new FileInputStream(SRC);
//			try (XWPFDocument xwpfDocument = new XWPFDocument(fis)) {
//				replacePlaceholdersInParagraphs(data, xwpfDocument);
//				replacePlaceholderInTables(data, xwpfDocument);
//
//				xwpfDocument.write(outputStream);
//
//				// Create target path using first name, last name and document name from this
//				String docNameFromDb = existingCustDoc.map(CustomerDocument::getDocName).orElse("unknown-document");
//				String docName = Optional.ofNullable(customerDocument.getDocName()).orElse(docNameFromDb);
//				String targetFileExt = getFileExtension(SRC);
//				String targetFilePath = String.format("%s%s-%s-%s.%s", DEST_PATH, firstName, lastName, docName,
//						targetFileExt);
//
//				Path path = Paths.get(targetFilePath);
//				System.out.println("File created at " + path);
//				Files.write(path, outputStream.toByteArray());
//				return path.toString();
//			} catch (Exception e) {
//				LOGGER.error("Error occurred while generating report: {}", e.getMessage());
//			}
//		}
//		return null;
//	}

	/**
	 * Method for replacing docx placeholders with given data parameters in every
	 * docx paragraph
	 *
	 * @param data         given data to be replaced with template placeholders
	 * @param xwpfDocument docx template document
	 **/
	private void replacePlaceholdersInParagraphs(Optional<HashMap<String, Object>> data, XWPFDocument xwpfDocument) {
		for (XWPFParagraph paragraph : xwpfDocument.getParagraphs()) {
			replaceParagraph(paragraph, data);
		}
	}

	/**
	 * Method for replacing docx placeholders with given data parameters in docx
	 * table
	 *
	 * @param data         given data to be replaced with template placeholders
	 * @param xwpfDocument docx template document
	 **/
	private void replacePlaceholderInTables(Optional<HashMap<String, Object>> data, XWPFDocument xwpfDocument) {
		for (XWPFTable xwpfTable : xwpfDocument.getTables()) {
			for (XWPFTableRow xwpfTableRow : xwpfTable.getRows()) {
				for (XWPFTableCell xwpfTableCell : xwpfTableRow.getTableCells()) {
					for (XWPFParagraph xwpfParagraph : xwpfTableCell.getParagraphs()) {
						replaceParagraph(xwpfParagraph, data);
					}
				}
			}
		}
	}

	private void replaceParagraph(XWPFParagraph paragraph, Optional<HashMap<String, Object>> data)
			throws POIXMLException {
		try {
			String find, text, runsText;
			List<XWPFRun> runs;
			XWPFRun run, nextRun;
			if (data.isPresent()) {
				HashMap<String, Object> items = data.get();
				for (Map.Entry<String, Object> entry : items.entrySet()) {
					String key = entry.getKey();
					String value = (String) entry.getValue();

					text = paragraph.getText();
					if (!text.contains("${")) {
						return;
					}
					find = "${" + key + "}";
					if (!text.contains(find)) {
						continue;
					}
					runs = paragraph.getRuns();
					for (int i = 0; i < runs.size(); i++) {
						run = runs.get(i);
						runsText = run.getText(0);
						if (runsText.contains("${")
								|| (runsText.contains("$") && runs.get(i + 1).getText(0).substring(0, 1).equals("{"))) {
							while (!openTagCountIsEqualCloseTagCount(runsText)) {
								nextRun = runs.get(i + 1);
								runsText = runsText + nextRun.getText(0);
								paragraph.removeRun(i + 1);
							}

							if (isImageFile(value)) {
								run.setText(runsText.contains(find) ? runsText.replace(find, "") : runsText, 0);
								insertImage(run, value);
							} else {
								run.setFontFamily(FONT2);
								run.setText(runsText.contains(find) ? runsText.replace(find, value) : runsText, 0);
							}
						}
					}
				}
			}
		} catch (Exception e) {
			LOGGER.error(e.getMessage());
		}
	}

	// As the next run may has a closed tag and an open tag at
	// the same time, we have to be sure that our building string
	// has a fully completed tags
	private boolean openTagCountIsEqualCloseTagCount(String runText) {
		int openTagCount = runText.split("\\$\\{", -1).length - 1;
		int closeTagCount = runText.split("}", -1).length - 1;
		return openTagCount == closeTagCount;
	}

	// Method to check if the path contains an image file with specific extensions
	public static boolean isImageFile(String filePath) {
		// Define the image file extensions
		String[] imageExtensions = { ".png", ".jpg", ".jpeg" };

		// Get the file extension from the file path
		Path path = Paths.get(filePath);
		String fileName = path.getFileName().toString().toLowerCase();

		// Check if the file name ends with any of the allowed extensions
		for (String extension : imageExtensions) {
			if (fileName.endsWith(extension)) {
				return true;
			}
		}

		return false;
	}

	// Helper method to insert image in XWPFRun
	private static void insertImage(XWPFRun r, String imagePath) throws Exception {
		try (FileInputStream imageStream = new FileInputStream(imagePath)) {
			r.addPicture(imageStream, Document.PICTURE_TYPE_PNG, imagePath, Units.toEMU(100), Units.toEMU(100));
		}
	}

	public String getFileExtension(String filePath) {
		if (filePath != null && filePath.contains(".")) {
			return filePath.substring(filePath.lastIndexOf(".") + 1);
		} else {
			return ""; // No extension found or filename is null
		}
	}
}