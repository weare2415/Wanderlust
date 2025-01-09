package com.wanderlust.payment.service;

import com.wanderlust.payment.dto.PaymentHistoryDTO;
import com.wanderlust.payment.dto.PaymentRequestDTO;
import com.wanderlust.payment.entity.Payment;
import com.wanderlust.payment.entity.PaymentStatus;
import com.wanderlust.payment.repository.PaymentRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    /**
     * 결제 처리
     */
    @Transactional
    public void processPayment(PaymentRequestDTO paymentRequest) {
        Payment payment = new Payment();
        payment.setReservatorName(paymentRequest.getReservatorName());
        payment.setReservatorEmail(paymentRequest.getReservatorEmail());
        payment.setReservatorPhone(paymentRequest.getReservatorPhone());
        payment.setPassengerNameEnglish(paymentRequest.getPassengerNameEnglish());
        payment.setPassengerBirthDate(paymentRequest.getPassengerBirthDate());
        payment.setPassengerGender(paymentRequest.getPassengerGender());
        payment.setPassportNumber(paymentRequest.getPassportNumber());
        payment.setPassportExpiryDate(paymentRequest.getPassportExpiryDate());
        payment.setNationality(paymentRequest.getNationality());
        payment.setImpUid(paymentRequest.getImpUid());
        payment.setMerchantUid(paymentRequest.getMerchantUid());
        payment.setAmount(paymentRequest.getAmount());
        payment.setCurrency("KRW");
        payment.setPaymentMethod("카드");
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setPaymentDate(LocalDate.now());

        // PassengerDTO -> Payment.Passenger 변환
        List<Payment.Passenger> passengers = paymentRequest.getCompanions()
                .stream()
                .map(dto -> {
                    Payment.Passenger passenger = new Payment.Passenger();
                    passenger.setNameEnglish(dto.getNameEnglish());
                    passenger.setBirthDate(dto.getBirthDate());
                    passenger.setGender(dto.getGender());
                    passenger.setPassportNumber(dto.getPassportNumber());
                    passenger.setPassportExpiryDate(dto.getPassportExpiryDate());
                    passenger.setNationality(dto.getNationality());
                    return passenger;
                })
                .collect(Collectors.toList());

        // 변환된 동승자 리스트를 Payment에 설정
        payment.setCompanions(passengers);

        paymentRepository.save(payment);
    }

    /**
     * 결제 상태를 완료로 업데이트
     */
    @Transactional
    public void updatePaymentStatusToCompleted(String impUid) {
        Payment payment = paymentRepository.findByImpUid(impUid)
                .orElseThrow(() -> new IllegalArgumentException("해당 결제를 찾을 수 없습니다."));

        payment.setPaymentStatus(PaymentStatus.SUCCESS); // 상태를 완료로 설정
        paymentRepository.save(payment);
    }

    /**
     * 결제 내역 조회
     */
    public List<PaymentHistoryDTO> getPaymentHistory() {
        return paymentRepository.findAll()
                .stream()
                .map(payment -> new PaymentHistoryDTO(
                        payment.getImpUid(),
                        payment.getMerchantUid(),
                        payment.getAmount(),
                        payment.getPaymentStatus().name(),
                        payment.getPaymentDate().atStartOfDay() // LocalDate → LocalDateTime
                ))
                .collect(Collectors.toList());
    }

    /**
     * 환불 요청 처리
     */
    @Transactional
    public void requestRefund(String impUid) {
        Payment payment = paymentRepository.findByImpUid(impUid)
                .orElseThrow(() -> new IllegalArgumentException("해당 결제를 찾을 수 없습니다."));

        if (payment.getPaymentStatus() != PaymentStatus.SUCCESS) {
            throw new IllegalStateException("환불 요청은 결제 성공 상태에서만 가능합니다.");
        }

        payment.setPaymentStatus(PaymentStatus.REFUND_REQUESTED);
        paymentRepository.save(payment);
    }

    /**
     * 환불 요청 승인 또는 거부
     */
    @Transactional
    public void processRefund(String impUid, boolean approve) {
        Payment payment = paymentRepository.findByImpUid(impUid)
                .orElseThrow(() -> new IllegalArgumentException("해당 결제를 찾을 수 없습니다."));

        if (payment.getPaymentStatus() != PaymentStatus.REFUND_REQUESTED) {
            throw new IllegalStateException("환불 처리는 요청 상태에서만 가능합니다.");
        }

        if (approve) {
            payment.setPaymentStatus(PaymentStatus.REFUNDED);
        } else {
            payment.setPaymentStatus(PaymentStatus.SUCCESS);
        }

        paymentRepository.save(payment);
    }
}