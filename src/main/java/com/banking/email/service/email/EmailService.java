package com.banking.email.service.email;

import com.banking.email.service.bo.ContactInformation;
import com.banking.email.service.repository.ContactRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import static com.banking.email.service.constants.EmailServiceConstants.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class EmailService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailService.class);
    private JavaMailSender mailSender;
    private ContactRepository contactRepository;
    private MessageSource messageSource;
    @Value("${from.email}")
    private String fromEmail;

    @Autowired
    public EmailService(JavaMailSender mailSender, ContactRepository contactRepository,MessageSource messageSource) {
        this.mailSender = mailSender;
        this.contactRepository = contactRepository;
        this.messageSource=messageSource;
    }

    @KafkaListener(topics = {TRANSACTION_TOPIC,CUSTOMER_ONBOARDING_TOPIC},groupId = "email-service")
    public void sendEmail(String kafkaMessage, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) throws JsonProcessingException {

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(kafkaMessage);
        Optional<ContactInformation> contactInformation = null;
        String customerId = root.get("customerId").toString();

        if (null != customerId) {
            contactInformation = contactRepository.findByCustomerId(Long.parseLong(customerId));
        }

        if (null != contactInformation && contactInformation.isPresent()) {
            send(contactInformation.get().getEmail(), root);
        }

    }

    private void send(String toEmail, JsonNode kafkaMessageRoot) {
        String subject = "";
        String messageTextBody="";

        if (null!=kafkaMessageRoot.get("transactionId")){
            subject= messageSource.getMessage("transaction.alert.email.subject.template",null,Locale.ENGLISH);
            messageTextBody=buildTransactionAlertMessage(kafkaMessageRoot);
        }else if (null!=kafkaMessageRoot.get("type")&&"CUSTOMER_ONBOARDING".equalsIgnoreCase(kafkaMessageRoot.get("type").asText())){
            subject=messageSource.getMessage("customer-onboarding.alert.email.subject.template",null,Locale.ENGLISH);
            messageTextBody=buildCustomerWelcomeAlertMessage(kafkaMessageRoot);
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(messageTextBody);
        mailSender.send(message);
        LOGGER.info("Email Sent Successfully");
    }

    private String buildCustomerWelcomeAlertMessage(JsonNode transactionObject) {
        String customerId = transactionObject.get("customerId").asText();
        LOGGER.info("Sending Email to the customer with customerId {}",customerId);
        String customerFirstname = transactionObject.get("customerFirstname").asText();
        return messageSource.getMessage("customer-onboarding.alert.email.body.template",
                new Object[]{customerFirstname,customerId},
                Locale.ENGLISH
                );
    }

    private String buildTransactionAlertMessage(JsonNode transactionObject){
        Object dateInObjectFormat = transactionObject.get("transactionDate");
        LocalDateTime txnTimestamp = null;
        if( dateInObjectFormat instanceof List){
            List<Integer> dateList =(List<Integer>) dateInObjectFormat;
            txnTimestamp = LocalDateTime.of(
                    dateList.get(0),
                    dateList.get(1),
                    dateList.get(2),
                    dateList.get(3),
                    dateList.get(4)
            );
        }
        String amount = String.format("%.2f",Double.parseDouble(transactionObject.get("transactionAmount").asText()));
       return messageSource.getMessage("transaction.alert.email.body.template",
               new Object[]{maskAccountNumber(transactionObject.get("accountNumber").asText()),
                       transactionObject.get("transactionType").asText().toLowerCase(),
                       amount,
                       transactionObject.get("transactionId").asText(),
                       txnTimestamp},
                Locale.ENGLISH);
    }

    private String maskAccountNumber(String accountNumber){
        StringBuilder stringBuilder= new StringBuilder();
        stringBuilder.append("XXXXXX");
        stringBuilder.append(accountNumber.substring(8));
        System.out.println(stringBuilder);
        return new String(stringBuilder);
    }
}
