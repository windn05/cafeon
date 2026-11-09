import React, { createContext, useState, useContext } from "react";

const ChatbotContext = createContext();

export const useChatbot = () => useContext(ChatbotContext);

export const ChatbotProvider = ({ children }) => {
    const [messages, setMessages] = useState([
        {
            sender: 'bot',
            content: '안녕하세요! CafeOn 운영에 대해 무엇이든 물어보세요.'
        }
    ]);

    const handleSend = async (newMessage) => {
        const userMessage = { sender: 'user', content: newMessage };
        setMessages(prev => [...prev, userMessage]);

        // TODO: 실제 LLM 백엔드와 연동 로직
        // try {
        //     const response = await fetch('여기서 api 주소 넣기', {
        //         method: 'POST',
        //         headers: { 'Content-Type': 'application/json' },
        //         body: JSON.stringify({ message: newMessage }),
        //     });
        //     const data = await response.json();
        //     const botMessage = { sender: 'bot', content: data.reply };
        //     setMessages(prev => [...prev, botMessage]);
        // } catch (error) {
        //     const errorMessage = { sender: 'bot', content: '죄송합니다, 답변 중 오류가 발생했습니다.' };
        //     setMessages(prev => [...prev, errorMessage]);
        // }

        // 임시 답변 로직
        return new Promise(resolve => {
            setTimeout(() => {
                const botMessage = {
                    sender: 'bot',
                    content: `"${newMessage}"에 대한 답변입니다.`,
                };
                setMessages(prev => [...prev, botMessage]);
                resolve();
            }, 1000);
        });
    };

    const value = { messages, handleSend };

    return (
        <ChatbotContext.Provider value={value}>
            {children}
        </ChatbotContext.Provider>
    );
};