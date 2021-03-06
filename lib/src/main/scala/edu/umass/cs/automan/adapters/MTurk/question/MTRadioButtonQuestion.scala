package edu.umass.cs.automan.adapters.MTurk.question

import edu.umass.cs.automan.core.answer.RadioButtonAnswer
import edu.umass.cs.automan.adapters.MTurk.{AutomanHIT, MTurkAdapter}
import actors.Future
import edu.umass.cs.automan.core.question.RadioButtonQuestion
import edu.umass.cs.automan.core.scheduler.Thunk
import com.amazonaws.mturk.requester.Assignment
import xml.XML
import java.security.MessageDigest
import org.apache.commons.codec.binary.Hex
import edu.umass.cs.automan.core.{LogType, LogLevel, Utilities}

object MTRadioButtonQuestion {
  def apply(init: MTRadioButtonQuestion => Unit, a: MTurkAdapter) : Future[RadioButtonAnswer] = {
    val radio_button_question = new MTRadioButtonQuestion
    init(radio_button_question)
    a.schedule(radio_button_question)
  }
}

class MTRadioButtonQuestion extends RadioButtonQuestion[MTQuestionOption] with MTurkQuestion {
  protected var _options = List[MTQuestionOption]()

  def answer(a: Assignment, is_dual: Boolean): RadioButtonAnswer = {
    // ignore is_dual
    new RadioButtonAnswer(None, a.getWorkerId, fromXML(XML.loadString(a.getAnswer)))
  }
  def build_hit(ts: List[Thunk], is_dual: Boolean) : AutomanHIT = {
    // we ignore the "dual" option here
    val x = toXML(false, true)
    val h = AutomanHIT { a =>
      a.hit_type_id = _hit_type_id
      a.title = text
      a.description = text
      a.keywords = _keywords
      a.question_xml = x
      a.assignmentDurationInSeconds = _worker_timeout_in_s
      a.lifetimeInSeconds = question_timeout_in_s
      a.maxAssignments = ts.size
      a.cost = ts.head.cost
      a.id = id
    }
    Utilities.DebugLog("Posting XML:\n" + x,LogLevel.INFO,LogType.ADAPTER,id)
    hits = h :: hits
    hit_thunk_map += (h -> ts)
    h
  }
  def memo_hash(dual: Boolean): String = {
    val md = MessageDigest.getInstance("md5")
    new String(Hex.encodeHex(md.digest(toXML(dual, false).toString.getBytes)))
  }
  def options: List[MTQuestionOption] = _options
  def options_=(os: List[MTQuestionOption]) { _options = os }
  def fromXML(x: scala.xml.Node) : Symbol = {
    // There should only be a SINGLE answer here, like this:
    //    <Answer>
    //      <QuestionIdentifier>721be9fc-c867-42ce-8acd-829e64ae62dd</QuestionIdentifier>
    //      <SelectionIdentifier>count</SelectionIdentifier>
    //    </Answer>

    Utilities.DebugLog("MTRadioButtonQuestion: fromXML:\n" + x.toString,LogLevel.INFO,LogType.ADAPTER,id)

    Symbol((x \\ "Answer" \\ "SelectionIdentifier").text)
  }
  def randomized_options: List[MTQuestionOption] = {
    import edu.umass.cs.automan.core.Utilities
    Utilities.randomPermute(options)
  }
  def toXML(is_dual: Boolean, randomize: Boolean) : scala.xml.Node = {
    // is_dual means nothing for this kind of question
    <QuestionForm xmlns="http://mechanicalturk.amazonaws.com/AWSMechanicalTurkDataSchemas/2005-10-01/QuestionForm.xsd">
      <Question>
        <QuestionIdentifier>{ if (randomize) id_string else "" }</QuestionIdentifier>
        <IsRequired>true</IsRequired>
        <QuestionContent>
          {
            _image_url match {
              case Some(url) => {
                <Binary>
                  <MimeType>
                    <Type>image</Type>
                    <SubType>png</SubType>
                  </MimeType>
                  <DataURL>{ url }</DataURL>
                  <AltText>{ image_alt_text }</AltText>
                </Binary>
              }
              case None => {}
            }
          }
          <Text>{ text }</Text>
        </QuestionContent>
        <AnswerSpecification>
          <SelectionAnswer>
            <StyleSuggestion>radiobutton</StyleSuggestion>
            <Selections>{ if(randomize) randomized_options.map { _.toXML } else options.map { _.toXML } }</Selections>
          </SelectionAnswer>
        </AnswerSpecification>
      </Question>
    </QuestionForm>
  }
}