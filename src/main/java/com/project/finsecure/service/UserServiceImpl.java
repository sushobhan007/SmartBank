package com.project.finsecure.service;

import com.project.finsecure.dto.*;
import com.project.finsecure.entity.User;
import com.project.finsecure.repository.UserRepository;
import com.project.finsecure.utils.AccountUtility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Override
    public BankResponse createAccount(UserRequest userRequest) {
        if (userRepository.existsByEmail(userRequest.getEmail())) {
            System.out.println("Account already exists for the email: " + userRequest.getEmail());
            return buildErrorResponse(AccountUtility.ACCOUNT_EXISTS_CODE,
                    AccountUtility.ACCOUNT_EXISTS_MESSAGE);
        }

        User newUser = buildNewUser(userRequest);
        User savedUser = userRepository.save(newUser);

        sendAccountCreationEmail(savedUser);

        System.out.println("Account created successfully for the user: " + savedUser.getEmail());
        return buildSuccessResponse(AccountUtility.ACCOUNT_CREATION_SUCCESS,
                AccountUtility.ACCOUNT_CREATION_SUCCESS_MESSAGE,
                savedUser);
    }

    @Override
    public BankResponse balanceEnquiry(EnquiryRequest enquiryRequest) {
        User user = getUserByAccountNumber(enquiryRequest.getAccountNumber());
        if (user == null) {
            return AccountUtility.accountNotExistResponse();
        }

        return buildSuccessResponse(AccountUtility.ACCOUNT_FOUND,
                AccountUtility.ACCOUNT_FOUND_MESSAGE,
                user);
    }

    @Override
    public String nameEnquiry(EnquiryRequest enquiryRequest) {
        User user = getUserByAccountNumber(enquiryRequest.getAccountNumber());
        return (user == null) ? AccountUtility.ACCOUNT_NOT_EXISTS_MESSAGE : getUserFullName(user);
    }

    @Override
    public BankResponse creditAccount(CreditDebitRequest creditDebitRequest) {
        User userToCredit = getUserByAccountNumber(creditDebitRequest.getAccountNumber());
        if (userToCredit == null) {
            return AccountUtility.accountNotExistResponse();
        }

        userToCredit.setAccountBalance(userToCredit.getAccountBalance().add(creditDebitRequest.getAmount()));
        userRepository.save(userToCredit);

        sendAccountCreditEmail(userToCredit, creditDebitRequest.getAmount());

        return buildSuccessResponse(AccountUtility.ACCOUNT_CREDITED_SUCCESS,
                AccountUtility.ACCOUNT_CREDITED_SUCCESS_MESSAGE,
                userToCredit);
    }

    @Override
    public BankResponse debitAccount(CreditDebitRequest creditDebitRequest) {
        User userToDebit = getUserByAccountNumber(creditDebitRequest.getAccountNumber());
        if (userToDebit == null) {
            return AccountUtility.accountNotExistResponse();
        }

        if (userToDebit.getAccountBalance().compareTo(creditDebitRequest.getAmount()) < 0) {
            return buildErrorResponse(AccountUtility.INSUFFICIENT_ACCOUNT_BALANCE,
                    AccountUtility.INSUFFICIENT_ACCOUNT_BALANCE_MESSAGE);
        }

        userToDebit.setAccountBalance(userToDebit.getAccountBalance().subtract(creditDebitRequest.getAmount()));
        userRepository.save(userToDebit);

        sendAccountDebitEmail(userToDebit, creditDebitRequest.getAmount());

        return buildSuccessResponse(AccountUtility.ACCOUNT_DEBITED_SUCCESS,
                AccountUtility.ACCOUNT_DEBITED_SUCCESS_MESSAGE,
                userToDebit);
    }

    @Override
    public BankResponse transfer(TransferRequest transferRequest) {

        //get the account to credit

        Boolean sourceAccountExists = userRepository.existsByAccountNumber(transferRequest.getSourceAccountNumber());

        //get the account to debit (check if account exists)
        Boolean destinationAccountExists = userRepository.existsByAccountNumber(transferRequest.getDestinationAccountNumber());
        if (!destinationAccountExists || !sourceAccountExists) {
            return AccountUtility.accountNotExistResponse();
        }
        User sourceAccountUser = userRepository.findByAccountNumber(transferRequest.getSourceAccountNumber());

        //check if debit amount is not more than the current amount
        if (sourceAccountUser.getAccountBalance().compareTo(transferRequest.getAmount()) < 0) {
            return buildErrorResponse(AccountUtility.INSUFFICIENT_ACCOUNT_BALANCE,
                    AccountUtility.INSUFFICIENT_ACCOUNT_BALANCE_MESSAGE);
        }

        //debit the amount
        sourceAccountUser.setAccountBalance(sourceAccountUser.getAccountBalance().subtract(transferRequest.getAmount()));
        userRepository.save(sourceAccountUser);

        //credit the account
        User destinationAccountUser = userRepository.findByAccountNumber(transferRequest.getDestinationAccountNumber());
        destinationAccountUser.setAccountBalance(destinationAccountUser.getAccountBalance().add(transferRequest.getAmount()));
        userRepository.save(destinationAccountUser);

        sendAccountDebitEmail(sourceAccountUser, transferRequest.getAmount());
        sendAccountCreditEmail(destinationAccountUser, transferRequest.getAmount());

        return buildSuccessResponse(AccountUtility.TRANSFER_SUCCESS,
                AccountUtility.TRANSFER_SUCCESS_MESSAGE,
                null);
    }

    private User getUserByAccountNumber(String accountNumber) {
        return userRepository.findByAccountNumber(accountNumber);
    }

    private String getUserFullName(User user) {
        return String.join(" ",
                user.getFirstName().trim(),
                user.getMiddleName().trim(),
                user.getLastName().trim());
    }

    private User buildNewUser(UserRequest userRequest) {
        return User.builder()
                .firstName(userRequest.getFirstName())
                .middleName(userRequest.getMiddleName())
                .lastName(userRequest.getLastName())
                .gender(userRequest.getGender())
                .address(userRequest.getAddress())
                .stateOfOrigin(userRequest.getStateOfOrigin())
                .accountNumber(AccountUtility.generateAccountNumber())
                .accountBalance(BigDecimal.ZERO)
                .email(userRequest.getEmail().toLowerCase())
                .phoneNumber(userRequest.getPhoneNumber())
                .alternativePhoneNumber(userRequest.getAlternativePhoneNumber())
                .status("ACTIVE")
                .build();
    }

    private void sendAccountCreationEmail(User savedUser) {
        String accountName = getUserFullName(savedUser);
        String emailBody = String.format("""
                Hello user,
                                
                Your account has been created successfully at our bank.
                Here are your details. Please save this email and keep the information safe.
                                
                Name: %s
                Account Number: %s
                Account Activation Date: %s
                                
                Thank you for using our service.
                Regards,
                FinSecure
                """, accountName, savedUser.getAccountNumber(), savedUser.getCreatedOn());

        EmailDetails emailDetails = EmailDetails.builder()
                .recipient(savedUser.getEmail())
                .subject("Account Created Successfully!")
                .messageBody(emailBody)
                .build();

        emailService.sendEmailAlert(emailDetails);
    }

    private void sendAccountDebitEmail(User savedUser, BigDecimal amount) {
        String accountName = getUserFullName(savedUser);
        String emailBody = String.format("""
                Hello %s,
                                
                We wish to inform you that INR %s has been debited from your A/C No. %s on %s 
                                
                Please call customer care if this transaction is not initiated by you.
                                
                Thank you for using our service.
                                
                Regards,
                FinSecure
                """, accountName, amount, savedUser.getAccountNumber(), savedUser.getCreatedOn());

        EmailDetails emailDetails = EmailDetails.builder()
                .recipient(savedUser.getEmail())
                .subject("Debit Notification Alert!")
                .messageBody(emailBody)
                .build();

        emailService.sendEmailAlert(emailDetails);
    }

    private void sendAccountCreditEmail(User savedUser, BigDecimal amount) {
        String accountName = getUserFullName(savedUser);
        String emailBody = String.format("""
                Hello %s,
                                
                INR %s has been credited to A/C No. %s on %s. 
                                
                For any concerns regarding this transaction, please call customer care.
                                
                Always open to help you.
                                
                Regards,
                FinSecure
                """, accountName, amount, savedUser.getAccountNumber(), savedUser.getCreatedOn());

        EmailDetails emailDetails = EmailDetails.builder()
                .recipient(savedUser.getEmail())
                .subject("Credit Notification from FinSecure!")
                .messageBody(emailBody)
                .build();

        emailService.sendEmailAlert(emailDetails);
    }

    private BankResponse buildErrorResponse(String responseCode, String responseMessage) {
        return BankResponse.builder()
                .responseCode(responseCode)
                .responseMessage(responseMessage)
                .accountInfo(null)
                .build();
    }

    private BankResponse buildSuccessResponse(String responseCode, String responseMessage, User user) {
        AccountInfo accountInfo = user == null ? null : AccountInfo.builder()
                .accountName(getUserFullName(user))
                .accountNumber(user.getAccountNumber())
                .accountBalance(user.getAccountBalance())
                .build();
        return BankResponse.builder()
                .responseCode(responseCode)
                .responseMessage(responseMessage)
                .accountInfo(accountInfo)
                .build();
    }
}
