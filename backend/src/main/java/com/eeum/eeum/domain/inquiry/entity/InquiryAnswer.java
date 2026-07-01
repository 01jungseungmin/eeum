package com.eeum.eeum.domain.inquiry.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.inquiry.enums.InquiryAnswerWriterType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "inquiry_answer",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_inquiry_answer_inquiry_id",
                        columnNames = "inquiry_id"
                )
        }
)
public class InquiryAnswer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "answer_id")
    private Long answerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inquiry_id", nullable = false)
    private Inquiry inquiry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account writer;

    @Enumerated(EnumType.STRING)
    @Column(name = "writer_type", nullable = false, length = 20)
    private InquiryAnswerWriterType writerType;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // ===================== 정적 팩토리 메서드 =====================

    public static InquiryAnswer create(
            Inquiry inquiry,
            Account writer,
            InquiryAnswerWriterType writerType,
            String content
    ) {
        InquiryAnswer answer = new InquiryAnswer();
        answer.inquiry = inquiry;
        answer.writer = writer;
        answer.writerType = writerType;
        answer.content = content;
        return answer;
    }
}
