package com.banking.email.service.email;

import com.banking.email.service.bo.ContactInformation;
import com.banking.email.service.repository.ContactRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import javax.swing.text.html.Option;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class EmailService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailService.class);
    private JavaMailSender mailSender;
    private ContactRepository contactRepository;

    @Autowired
    public EmailService(JavaMailSender mailSender, ContactRepository contactRepository) {
        this.mailSender = mailSender;
        this.contactRepository = contactRepository;
    }

    @KafkaListener(topics = "TRANSACTION",groupId = "email-service")
    public void sendSimpleEmail(String transaction) throws JsonProcessingException {

        ObjectMapper mapper= new ObjectMapper();
        Optional<ContactInformation> contactInformation=null;
        Map<String,Object> transactionObject = mapper.readValue(transaction,Map.class);
        String customerId = transactionObject.get("customerId").toString();

        if (null!=transactionObject.get("customerId")){
         contactInformation  =  contactRepository.findByCustomerId(Long.parseLong(customerId));
        }
        if (null!=contactInformation && contactInformation.isPresent()){
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("anigayake101@gmail.com");
            message.setTo(contactInformation.get().getEmail());
            message.setSubject("Transaction Alert for your Apna Bank Mobile Banking");
            message.setText(getTransactionText(transactionObject));
            mailSender.send(message);
            System.out.println(transaction);
            LOGGER.info("Email Sent Successfully");
        }
    }

    private String getTransactionText(Map<String, Object> transactionObject) {
        return "Dear Customer,\n\n" +
                "Your account " +transactionObject.get("accountNumber")+" has been "+ transactionObject.get("transactionType").toString().toLowerCase() +
                " for amount of INR " + transactionObject.get("transactionAmount")+
                ". The Payment was initiated through Transaction ID: "+ transactionObject.get("transactionId").toString() + " on "+
                transactionObject.get("transactionDate")+ ".\n\n" +
                "\n In case you have not done this transaction, please call our Customer care.\n"+
                "\n\nNever share your OTP, PIN URN, CVV or passwords with anyone even if the person claims to be a Bank Employee.\n"+
                "\nSincerely,\n"+
                "Team Apna bank\n\n" +
                "This is a auto-generated e-mail, please do not reply to this e-mail";

    }


}
