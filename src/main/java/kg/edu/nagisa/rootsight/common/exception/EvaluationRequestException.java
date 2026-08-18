package kg.edu.nagisa.rootsight.common.exception;

import lombok.experimental.StandardException;

/**
 * 评测场景不完整、互相矛盾或超过资源边界时抛出的统一业务异常。
 */
@StandardException
public class EvaluationRequestException extends RuntimeException {
}
