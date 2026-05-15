describe('ML Pipeline feature', () => {
  const sessionId = 321;
  const currentQuestion = {
    id: 987,
    roleType: 'AI',
    type: 'TECHNICAL',
    domain: 'ml_pipeline',
    difficulty: 'INTERMEDIATE',
    questionText: 'Walk me through a complete ML pipeline from raw data to a deployed model.',
    expectedPoints: '["Data ingestion", "Feature engineering", "Deployment"]',
    ttsAudioUrl: null,
  };

  const sessionResponse = {
    id: sessionId,
    userId: 1,
    careerPathId: 1,
    roleType: 'AI',
    mode: 'PRACTICE',
    type: 'TECHNICAL',
    status: 'IN_PROGRESS',
    currentQuestionIndex: 0,
    durationSeconds: 0,
  };

  function interceptSessionApis(roleType: 'AI' | 'SE' = 'AI') {
    cy.intercept('POST', '**/interview-service/api/v1/sessions/start', {
      statusCode: 201,
      body: {
        ...sessionResponse,
        roleType,
      },
    }).as('startSession');

    cy.intercept('GET', `**/interview-service/api/v1/sessions/${sessionId}`, {
      statusCode: 200,
      body: {
        ...sessionResponse,
        roleType,
      },
    }).as('getSession');

    cy.intercept('GET', `**/interview-service/api/v1/sessions/${sessionId}/questions/current`, {
      statusCode: 200,
      body: {
        ...currentQuestion,
        roleType,
      },
    }).as('getCurrentQuestion');

    cy.intercept('GET', `**/interview-service/api/v1/sessions/${sessionId}/questions`, {
      statusCode: 200,
      body: [
        {
          id: 1,
          sessionId,
          questionId: currentQuestion.id,
          questionOrder: 0,
          wasSkipped: false,
          question: {
            ...currentQuestion,
            roleType,
          },
        },
      ],
    }).as('getQuestionOrder');

    cy.intercept('GET', `**/interview-service/api/v1/answers/session/${sessionId}`, {
      statusCode: 200,
      body: [],
    }).as('getAnswers');

    cy.intercept('GET', `**/interview-service/api/v1/evaluations/session/${sessionId}`, {
      statusCode: 200,
      body: [],
    }).as('getEvaluations');
  }

  function visitSession(roleType: 'AI' | 'SE' = 'AI') {
    interceptSessionApis(roleType);
    cy.visit(`/dashboard/interview/session/${sessionId}`);
    cy.wait('@getSession');
    cy.wait('@getCurrentQuestion');
    cy.wait('@getQuestionOrder');
    cy.wait('@getAnswers');
    cy.wait('@getEvaluations');
  }

  it('CY-1 mlPipelineScreenRenders_forAIRole', () => {
    visitSession('AI');

    cy.get('[data-testid="ml-pipeline-panel"]', { timeout: 10000 }).should('exist');
    cy.get('[data-testid="stage-card"]').should('have.length', 7);
  });

  it('CY-2 allSevenStagesRender_withCorrectLabels', () => {
    visitSession('AI');

    const expectedLabels = [
      'Data Ingestion',
      'Preprocessing',
      'Feature Engineering',
      'Model Training',
      'Evaluation',
      'Deployment',
      'Monitoring',
    ];

    expectedLabels.forEach((label) => {
      cy.contains(label).should('exist');
    });
  });

  it('CY-3 stageDragAndDrop_reordersCards', () => {
    visitSession('AI');

    cy.get('[data-testid="stage-card"]').first().should('contain', 'Data Ingestion');
    cy.get('[data-testid="stage-card"]').then(($cardsBefore) => {
      const beforeCount = $cardsBefore.length;

      cy.get('[data-testid="stage-card"]').first().trigger('pointerdown', {
        button: 0,
        pointerId: 1,
        force: true,
        clientX: 300,
        clientY: 220,
      });
      cy.get('[data-testid="stage-card"]').last().trigger('pointermove', {
        pointerId: 1,
        force: true,
        clientX: 300,
        clientY: 640,
      });
      cy.get('[data-testid="stage-card"]').last().trigger('pointerup', {
        pointerId: 1,
        force: true,
      });

      cy.get('[data-testid="stage-card"]').should('have.length', beforeCount);
      cy.get('[data-testid="stage-card"]').first().should('contain', 'Data Ingestion');
    });
  });

  it('CY-4 keywordHighlights_afterAnswerSubmit', () => {
    interceptSessionApis('AI');

    cy.intercept('POST', '**/interview-service/api/v1/answers/submit', {
      statusCode: 202,
      body: {
        id: 444,
        sessionId,
        questionId: currentQuestion.id,
      },
    }).as('submitAnswer');

    cy.intercept('POST', '**/interview-service/api/v1/ml-answers/extract/*', {
      statusCode: 202,
      body: {},
    }).as('extractMl');

    cy.intercept('GET', '**/interview-service/api/v1/ml-answers/answer/*', {
      fixture: 'ml-answer-extracted.json',
    }).as('getMLAnswer');

    cy.visit(`/dashboard/interview/session/${sessionId}`);
    cy.wait('@getSession');
    cy.wait('@getCurrentQuestion');
    cy.wait('@getQuestionOrder');
    cy.get('[data-testid="ml-pipeline-panel"]', { timeout: 10000 }).should('exist');

    cy.get('textarea').first().type('I would use XGBoost, evaluate with F1 and AUC, and deploy via FastAPI.');
    cy.contains('Submit Typed Answer').click();

    cy.wait('@submitAnswer');
    cy.wait('@extractMl');
    cy.wait('@getMLAnswer');
    cy.wait(1200);

    cy.get('[data-testid="stage-card"][data-stage="evaluation"]').should('have.class', 'highlighted');
    cy.get('[data-testid="stage-counter"]').should('contain', '/ 7 stages mentioned');
  });

  it('CY-5 followUpBubble_appearsAfterExtraction', () => {
    interceptSessionApis('AI');

    cy.intercept('POST', '**/interview-service/api/v1/answers/submit', {
      statusCode: 202,
      body: {
        id: 445,
        sessionId,
        questionId: currentQuestion.id,
      },
    }).as('submitAnswer');

    cy.intercept('POST', '**/interview-service/api/v1/ml-answers/extract/*', {
      statusCode: 202,
      body: {},
    }).as('extractMl');

    cy.intercept('GET', '**/interview-service/api/v1/ml-answers/answer/*', {
      fixture: 'ml-answer-extracted.json',
    }).as('getMLAnswer');

    cy.visit(`/dashboard/interview/session/${sessionId}`);
    cy.wait('@getSession');
    cy.wait('@getCurrentQuestion');
    cy.wait('@getQuestionOrder');
    cy.get('[data-testid="ml-pipeline-panel"]', { timeout: 10000 }).should('exist');

    cy.get('textarea').first().type('XGBoost with FastAPI deployment and F1/AUC evaluation.');
    cy.contains('Submit Typed Answer').click();

    cy.wait('@submitAnswer');
    cy.wait('@extractMl');
    cy.wait('@getMLAnswer');

    cy.get('[data-testid="follow-up-bubble"]').should('exist');
    cy.get('[data-testid="follow-up-bubble"]').should('contain', 'XGBoost');
  });

  it('CY-6 mlPipelinePanel_notShown_forSERole', () => {
    visitSession('SE');

    cy.get('[data-testid="ml-pipeline-panel"]').should('not.exist');
    cy.get('app-verbal-interview').should('exist');
  });
});
