// Copyright: 2010 - 2017 https://github.com/ensime/ensime-server/graphs
// License: http://www.gnu.org/licenses/gpl-3.0.en.html
package org.ensime.server.protocol.swank

import java.io.File

import org.ensime.api._
import org.ensime.sexp._
import org.ensime.util.{ EnsimeSpec, EscapingStringInterpolation }
import org.scalactic.source.Position

class SwankFormatsSpec extends EnsimeSpec with EnsimeTestData {
  import SwankFormats._
  import SwankTestData._
  import EscapingStringInterpolation._

  def marshal(value: EnsimeServerMessage, via: Option[String])(implicit p: Position): Unit = {
    val envelope = value match {
      case r: RpcResponse => RpcResponseEnvelope(Some(666), value)
      case e: EnsimeEvent => RpcResponseEnvelope(None, value)
    }
    val sexp = envelope.toSexp match {
      case SexpList(
        SexpSymbol(":return") ::
          SexpList(SexpSymbol(":ok") :: payload :: Nil) ::
          SexpNumber(callId) :: Nil
        ) if callId == 666 => payload
      case payload => payload
    }
    via match {
      case None => println(s"$value = ${sexp.compactPrint}")
      // using String form because SexpSymbol("nil") for BasicTypeHint is not commutative
      case Some(expected) => sexp.compactPrint shouldBe expected
    }
  }
  def marshal(value: EnsimeServerMessage, via: String)(implicit p: Position): Unit = marshal(value, Some(via))

  def unmarshal(from: String, to: RpcRequest)(implicit p: Position): Unit = {
    val sexp = s"(:swank-rpc ${from} 666)"
    //println(sexp + " => " + sexp.parseSexp)
    sexp.parseSexp.convertTo[RpcRequestEnvelope].req shouldBe to
  }

  implicit def toFile(raw: RawFile): File = raw.file.toFile

  "SWANK Formats" should "unmarshal startup messages" in {
    unmarshal(
      "(swank:connection-info)",
      ConnectionInfoReq: RpcRequest
    )
  }

  it should "unmarshal RpcSearchRequests" in {
    unmarshal(
      """(swank:public-symbol-search ("foo" "bar") 10)""",
      PublicSymbolSearchReq(List("foo", "bar"), 10): RpcRequest
    )

    unmarshal(
      s"""(swank:import-suggestions "$file1" 1 ("foo" "bar") 10)""",
      ImportSuggestionsReq(Left(file1), 1, List("foo", "bar"), 10): RpcRequest
    )
  }

  it should "unmarshal RpcAnalyserRequests" in {
    unmarshal(
      s"""(swank:remove-file "$file1")""",
      RemoveFileReq(file1): RpcRequest
    )

    unmarshal(
      s"""(swank:typecheck-file (:file "$file1" :contents "{/* code here */}" :contents-in "$file2"))""",
      TypecheckFileReq(sourceFileInfo): RpcRequest
    )

    unmarshal(
      s"""(swank:typecheck-files ("$file1" "$file2"))""",
      TypecheckFilesReq(List(Left(file1), Left(file2))): RpcRequest
    )

    unmarshal(
      s"""(swank:typecheck-files ((:file "$file1") (:file "$file2" :contents "xxx")))""",
      TypecheckFilesReq(List(Right(SourceFileInfo(file1)), Right(SourceFileInfo(file2, Some("xxx"), None)))): RpcRequest
    )

    unmarshal(
      """(swank:restart-scala-compiler nil keep)""",
      RestartScalaCompilerReq(None, ReloadStrategy.KeepLoaded): RpcRequest
    )

    unmarshal(
      s"""(swank:doc-uri-at-point "$file1" (1 10))""",
      DocUriAtPointReq(Left(file1), OffsetRange(1, 10)): RpcRequest
    )

    unmarshal(
      s"""(swank:doc-uri-at-point (:file "$file1" :contents-in "$file2") (1 10))""",
      DocUriAtPointReq(Right(SourceFileInfo(file1, None, Some(file2))), OffsetRange(1, 10)): RpcRequest
    )

    unmarshal(
      s"""(swank:completions (:file "$file1" :contents "{/* code here */}" :contents-in "$file2") 10 100 t nil)""",
      CompletionsReq(sourceFileInfo, 10, 100, true, false): RpcRequest
    )

    unmarshal(
      s"""(swank:uses-of-symbol-at-point (:file "$file1" :contents "{/* code here */}" :contents-in "$file2") 100)""",
      UsesOfSymbolAtPointReq(sourceFileInfo, 100): RpcRequest
    )

    unmarshal(
      s"""(swank:hierarchy-of-type-at-point (:file "$file1" :contents "{/* code here */}" :contents-in "$file2") 100)""",
      HierarchyOfTypeAtPointReq(sourceFileInfo, 100): RpcRequest
    )

    unmarshal(
      s"""(swank:type-at-point "$file1" (1 100))""",
      TypeAtPointReq(Left(file1), OffsetRange(1, 100)): RpcRequest
    )

    unmarshal(
      s"""(swank:symbol-at-point "$file1" 101)""",
      SymbolAtPointReq(Left(file1), 101): RpcRequest
    )

    unmarshal(
      s"""(swank:diff-refactor 1 (end 100 file "$file1" newName "bar" start 1) nil)""",
      RefactorReq(1, RenameRefactorDesc("bar", file1, 1, 100), false): RpcRequest
    )

    unmarshal(
      s"""(swank:diff-refactor 1 (end 100 file "$file1" start 1) nil)""",
      RefactorReq(1, InlineLocalRefactorDesc(file1, 1, 100), false): RpcRequest
    )

    unmarshal(
      s"""(swank:diff-refactor 1 (end 100 file "$file1" start 1 tpe "expandMatchCases") nil)""",
      RefactorReq(1, ExpandMatchCasesDesc(file1, 1, 100), false): RpcRequest
    )

    unmarshal(
      s"""(swank:symbol-designations "$file1" 1 100 (object val))""",
      SymbolDesignationsReq(
        Left(file1), 1, 100,
        List(ObjectSymbol, ValSymbol)
      ): RpcRequest
    )

    unmarshal(
      s"""(swank:symbol-designations (:file "$file1") 1 100 (object val))""",
      SymbolDesignationsReq(
        Right(SourceFileInfo(file1, None, None)), 1, 100,
        List(ObjectSymbol, ValSymbol)
      ): RpcRequest
    )

    unmarshal(
      s"""(swank:expand-selection "$file1" 100 200)""",
      ExpandSelectionReq(file1, 100, 200): RpcRequest
    )

    unmarshal(
      s"""(swank:implicit-info "$file1" (0 123))""",
      ImplicitInfoReq(Left(file1), OffsetRange(0, 123))
    )

    unmarshal(
      s"""(swank:structure-view (:file "$file1" :contents "{/* code here */}" :contents-in "$file2"))""",
      StructureViewReq(sourceFileInfo): RpcRequest
    )

    unmarshal(
      s"""(swank:unload-file (:file "$file1"))""",
      UnloadFileReq(sourceFileInfo2): RpcRequest
    )
  }

  it should "unmarshal RpcDebugRequests" in {
    unmarshal(
      """(swank:debug-active-vm)""",
      DebugActiveVmReq: RpcRequest
    )

    unmarshal(
      """(swank:debug-attach "mylovelyhorse" "13")""",
      DebugAttachReq("mylovelyhorse", "13"): RpcRequest
    )

    unmarshal(
      """(swank:debug-stop)""",
      DebugStopReq: RpcRequest
    )

    unmarshal(
      s"""(swank:debug-set-break "$file1" 13)""",
      DebugSetBreakReq(file1, 13): RpcRequest
    )

    unmarshal(
      s"""(swank:debug-clear-break "$file1" 13)""",
      DebugClearBreakReq(file1, 13): RpcRequest
    )

    unmarshal(
      s"""(swank:debug-clear-all-breaks)""",
      DebugClearAllBreaksReq: RpcRequest
    )

    unmarshal(
      s"""(swank:debug-list-breakpoints)""",
      DebugListBreakpointsReq: RpcRequest
    )

    unmarshal(
      s"""(swank:debug-run)""",
      DebugRunReq: RpcRequest
    )

    unmarshal(
      s"""(swank:debug-continue "13")""",
      DebugContinueReq(dtid): RpcRequest
    )

    unmarshal(
      s"""(swank:debug-step "13")""",
      DebugStepReq(dtid): RpcRequest
    )

    unmarshal(
      s"""(swank:debug-next "13")""",
      DebugNextReq(dtid): RpcRequest
    )

    unmarshal(
      s"""(swank:debug-step-out "13")""",
      DebugStepOutReq(dtid): RpcRequest
    )

    unmarshal(
      s"""(swank:debug-locate-name "13" "foo")""",
      DebugLocateNameReq(dtid, "foo"): RpcRequest
    )

    unmarshal(
      s"""(swank:debug-value (:type element :object-id "13" :index 14))""",
      DebugValueReq(debugLocationArray): RpcRequest
    )

    unmarshal(
      s"""(swank:debug-to-string "13" (:type element :object-id "13" :index 14))""",
      DebugToStringReq(dtid, debugLocationArray): RpcRequest
    )

    unmarshal(
      s"""(swank:debug-set-value (:type element :object-id "13" :index 14) "bar")""",
      DebugSetValueReq(debugLocationArray, "bar"): RpcRequest
    )

    unmarshal(
      s"""(swank:debug-backtrace "13" 100 200)""",
      DebugBacktraceReq(dtid, 100, 200): RpcRequest
    )

  }

  it should "marshal EnsimeGeneralEvent as EnsimeEvent" in {
    marshal(
      SendBackgroundMessageEvent("ABCDEF", 1): EnsimeEvent,
      """(:background-message 1 "ABCDEF")"""
    )

    marshal(
      AnalyzerReadyEvent: EnsimeEvent,
      "(:compiler-ready)"
    )

    marshal(
      FullTypeCheckCompleteEvent: EnsimeEvent,
      "(:full-typecheck-finished)"
    )

    marshal(
      IndexerReadyEvent: EnsimeEvent,
      "(:indexer-ready)"
    )

    marshal(
      NewScalaNotesEvent(
        isFull = false,
        List(Note("foo.scala", "testMsg", NoteWarn, 50, 55, 77, 5))
      ): EnsimeEvent,
      """(:scala-notes (:notes ((:file "foo.scala" :msg "testMsg" :severity warn :beg 50 :end 55 :line 77 :col 5))))"""
    )

    marshal(
      ClearAllScalaNotesEvent: EnsimeEvent,
      "(:clear-all-scala-notes)"
    )
  }

  it should "marshal DebugEvent as EnsimeEvent" in {
    marshal(
      DebugOutputEvent("XXX"): EnsimeEvent,
      """(:debug-event (:type output :body "XXX"))"""
    )

    marshal(
      DebugStepEvent(DebugThreadId(207), "threadNameStr", sourcePos1.file, sourcePos1.line): EnsimeEvent,
      s"""(:debug-event (:type step :thread-id "207" :thread-name "threadNameStr" :file "$file1" :line 57))"""
    )

    marshal(
      DebugBreakEvent(DebugThreadId(209), "threadNameStr", sourcePos1.file, sourcePos1.line): EnsimeEvent,
      s"""(:debug-event (:type breakpoint :thread-id "209" :thread-name "threadNameStr" :file "$file1" :line 57))"""
    )

    marshal(
      DebugVmStartEvent: EnsimeEvent,
      """(:debug-event (:type start))"""
    )
    marshal(
      DebugVmDisconnectEvent: EnsimeEvent,
      """(:debug-event (:type disconnect))"""
    )
    marshal(
      DebugExceptionEvent(33L, dtid, "threadNameStr", Some(sourcePos1.file), Some(sourcePos1.line)): EnsimeEvent,
      s"""(:debug-event (:type exception :exception 33 :thread-id "13" :thread-name "threadNameStr" :file "$file1" :line 57))"""
    )
    marshal(
      DebugExceptionEvent(33L, dtid, "threadNameStr", None, None): EnsimeEvent,
      """(:debug-event (:type exception :exception 33 :thread-id "13" :thread-name "threadNameStr"))"""
    )

    marshal(
      DebugThreadStartEvent(dtid): EnsimeEvent,
      """(:debug-event (:type threadStart :thread-id "13"))"""
    )
    marshal(
      DebugThreadDeathEvent(dtid): EnsimeEvent,
      """(:debug-event (:type threadDeath :thread-id "13"))"""
    )
  }

  it should "marshal DebugLocation" in {
    marshal(
      DebugObjectReference(57L): DebugLocation,
      """(:type reference :object-id "57")"""
    )

    marshal(
      DebugArrayElement(DebugObjectId(58L), 2): DebugLocation,
      """(:type element :object-id "58" :index 2)"""
    )

    marshal(
      DebugObjectField(DebugObjectId(58L), "fieldName"): DebugLocation,
      """(:type field :object-id "58" :field "fieldName")"""
    )

    marshal(
      DebugStackSlot(DebugThreadId(27), 12, 23): DebugLocation,
      """(:type slot :thread-id "27" :frame 12 :offset 23)"""
    )
  }

  it should "marshal DebugValue" in {
    marshal(
      DebugPrimitiveValue("summaryStr", "typeNameStr"): DebugValue,
      """(:val-type prim :summary "summaryStr" :type-name "typeNameStr")"""
    )

    marshal(
      DebugStringInstance("summaryStr", List(debugClassField), "typeNameStr", DebugObjectId(5L)): DebugValue,
      """(:val-type str :summary "summaryStr" :fields ((:index 19 :name "nameStr" :type-name "typeNameStr" :summary "summaryStr")) :type-name "typeNameStr" :object-id "5")"""
    )

    marshal(
      DebugObjectInstance("summaryStr", List(debugClassField), "typeNameStr", DebugObjectId(5L)): DebugValue,
      """(:val-type obj :summary "summaryStr" :fields ((:index 19 :name "nameStr" :type-name "typeNameStr" :summary "summaryStr")) :type-name "typeNameStr" :object-id "5")"""
    )

    marshal(
      DebugNullValue("typeNameStr"): DebugValue,
      """(:val-type null :type-name "typeNameStr")"""
    )

    marshal(
      DebugArrayInstance(3, "typeName", "elementType", DebugObjectId(5L)): DebugValue,
      """(:val-type arr :length 3 :type-name "typeName" :element-type-name "elementType" :object-id "5")"""
    )

    marshal(
      debugClassField: DebugClassField,
      """(:index 19 :name "nameStr" :type-name "typeNameStr" :summary "summaryStr")"""
    )

    marshal(
      debugStackLocal1: DebugStackLocal,
      """(:index 3 :name "name1" :summary "summary1" :type-name "type1")"""
    )

    marshal(
      debugStackFrame: DebugStackFrame,
      s"""(:index 7 :locals ((:index 3 :name "name1" :summary "summary1" :type-name "type1") (:index 4 :name "name2" :summary "summary2" :type-name "type2")) :num-args 4 :class-name "class1" :method-name "method1" :pc-location (:file "$file1" :line 57) :this-object-id "7")"""
    )

    marshal(
      DebugBacktrace(List(debugStackFrame), dtid, "thread1"): DebugBacktrace,
      s"""(:frames ((:index 7 :locals ((:index 3 :name "name1" :summary "summary1" :type-name "type1") (:index 4 :name "name2" :summary "summary2" :type-name "type2")) :num-args 4 :class-name "class1" :method-name "method1" :pc-location (:file "$file1" :line 57) :this-object-id "7")) :thread-id "13" :thread-name "thread1")"""
    )

    marshal(
      sourcePos1: SourcePosition,
      s"""(:type line :file "$file1" :line 57)"""
    )
    marshal(
      sourcePos2: SourcePosition,
      s"""(:type line :file "$file1" :line 59)"""
    )
    marshal(
      sourcePos3: SourcePosition,
      "(:type empty)"
    )
    marshal(
      sourcePos4: SourcePosition,
      s"""(:type offset :file "$file1" :offset 456)"""
    )

    marshal(
      breakPoint1: Breakpoint,
      s"""(:file "$file1" :line 57)"""
    )

    marshal(
      BreakpointList(List(breakPoint1), List(breakPoint2)): BreakpointList,
      s"""(:active ((:file "$file1" :line 57)) :pending ((:file "$file1" :line 59)))"""
    )

    marshal(
      DebugVmSuccess(): DebugVmStatus,
      """(:type success :status "success")"""
    )

    marshal(
      DebugVmError(303, "xxxx"): DebugVmStatus,
      """(:type error :error-code 303 :details "xxxx" :status "error")"""
    )
  }

  it should "marshal various informational types" in {
    marshal(
      note1: Note,
      note1Str
    )

    marshal(
      completionInfo: CompletionInfo,
      """(:type-info (:arrow-type nil :name "type1" :decl-as method :full-name "FOO.type1") :name "name" :relevance 90 :to-insert "BAZ")"""
    )

    marshal(
      completionInfo2: CompletionInfo,
      """(:name "nam" :relevance 91 :is-infix t)"""
    )

    marshal(
      CompletionInfoList("fooBar", List(completionInfo)): CompletionInfoList,
      """(:prefix "fooBar" :completions ((:type-info (:arrow-type nil :name "type1" :decl-as method :full-name "FOO.type1") :name "name" :relevance 90 :to-insert "BAZ")))"""
    )

    marshal(
      SymbolInfo("name", "localName", None, typeInfo): SymbolInfo,
      """(:name "name" :local-name "localName" :type (:arrow-type nil :name "type1" :decl-as method :full-name "FOO.type1"))"""
    )

    marshal(
      NamedTypeMemberInfo("typeX", typeInfo, None, None, DeclaredAs.Method): EntityInfo,
      """(:name "typeX" :type (:arrow-type nil :name "type1" :decl-as method :full-name "FOO.type1") :decl-as method)"""
    )

    marshal(
      entityInfo: EntityInfo,
      entityInfoStr
    )

    marshal(
      entityInfoTypeParams: EntityInfo,
      entityInfoTypeParamsStr
    )

    marshal(
      typeInfo: EntityInfo,
      typeInfoStr
    )

    marshal(
      packageInfo: EntityInfo,
      """(:info-type package :name "name" :full-name "fullName")"""
    )

    marshal(
      interfaceInfo: InterfaceInfo,
      """(:type (:arrow-type nil :name "type1" :decl-as method :full-name "FOO.type1") :via-view "DEF")"""
    )

    marshal(
      structureView: StructureView,
      s"""(:view ((:keyword "class" :name "StructureView" :position (:type line :file "$file1" :line 57)) (:keyword "object" :name "StructureView" :position (:type line :file "$file1" :line 59) :members ((:keyword "type" :name "BasicType" :position (:type offset :file "$file1" :offset 456))))))"""
    )
  }

  it should "marshal search related responses" in {
    marshal(
      SymbolSearchResults(List(methodSearchRes, typeSearchRes)): SymbolSearchResults,
      s"""((:type method :name "abc" :local-name "a" :decl-as method :pos (:type line :file "$abd" :line 10) :owner-name "ownerStr") (:type type :name "abc" :local-name "a" :decl-as trait :pos (:type line :file "$abd" :line 10)))"""
    )

    marshal(
      ImportSuggestions(List(List(methodSearchRes, typeSearchRes))): ImportSuggestions,
      s"""(((:type method :name "abc" :local-name "a" :decl-as method :pos (:type line :file "$abd" :line 10) :owner-name "ownerStr") (:type type :name "abc" :local-name "a" :decl-as trait :pos (:type line :file "$abd" :line 10))))"""
    )

    marshal(
      methodSearchRes: SymbolSearchResult,
      s"""(:type method :name "abc" :local-name "a" :decl-as method :pos (:type line :file "$abd" :line 10) :owner-name "ownerStr")"""
    )

    marshal(
      typeSearchRes: SymbolSearchResult,
      s"""(:type type :name "abc" :local-name "a" :decl-as trait :pos (:type line :file "$abd" :line 10))"""
    )

    marshal(
      SourcePositions(sourcePos2 :: Nil),
      s"""((:type line :file "$file1" :line 59))"""
    )

    marshal(
      hierarchyInfo: HierarchyInfo,
      s"""(:ancestors ((:fqn "java.lang.object" :decl-as class)) :inheritors ((:scala-name "def.foo" :fqn "def$$foo" :decl-as class :source-position (:type line :file "$file1" :line 59))))"""
    )
  }

  it should "marshal ranges and semantic highlighting" in {
    marshal(
      FileRange("/abc", 7, 9): FileRange,
      """(:file "/abc" :start 7 :end 9)"""
    )

    marshal(
      SymbolDesignations(
        symFile, List(
        SymbolDesignation(7, 9, VarFieldSymbol),
        SymbolDesignation(11, 22, ClassSymbol)
      )
      ): SymbolDesignations,
      s"""(:file "$symFile" :syms ((varField 7 9) (class 11 22)))"""
    )

    marshal(
      ImplicitInfos(List(ImplicitConversionInfo(5, 6, symbolInfo))): ImplicitInfos,
      s"""((:type conversion :start 5 :end 6 :fun $symbolInfoStr))"""
    )

    marshal(
      ImplicitInfos(List(ImplicitParamInfo(5, 6, symbolInfo, List(symbolInfo, symbolInfo), true))): ImplicitInfos,
      s"""((:type param :start 5 :end 6 :fun $symbolInfoStr :params ($symbolInfoStr $symbolInfoStr) :fun-is-implicit t))"""
    )
  }

  it should "marshal refactoring messages" in {
    marshal(
      RefactorFailure(7, "message"): RefactorFailure,
      """(:procedure-id 7 :reason "message" :status failure)"""
    )

    marshal(
      refactorDiffEffect: RefactorDiffEffect,
      s"""(:procedure-id 9 :refactor-type addImport :diff "$file2")"""
    )

  }

  it should "marshal legacy raw response types" in {
    marshal(
      FalseResponse,
      "nil"
    )

    marshal(
      TrueResponse,
      "t"
    )

    marshal(
      StringResponse("wibble"),
      """"wibble""""
    )

    marshal(
      VoidResponse,
      """nil"""
    )

  }

}
